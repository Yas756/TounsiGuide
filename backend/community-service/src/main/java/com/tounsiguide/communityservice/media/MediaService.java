package com.tounsiguide.communityservice.media;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.PublicationStatus;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Photos and videos of posts and comments, in two steps: {@link #upload} stores the file and gives an id, then the
 * post or comment is created or edited with that id in {@code mediaIds} ({@link #attachToPost}, {@link #replacePostMedia}...).
 */
@Service
public class MediaService {

    public static final int MAX_MEDIA_PER_POST = 6;
    public static final int MAX_VIDEOS_PER_POST = 1;
    public static final int MAX_MEDIA_PER_COMMENT = 1;

    static final String FILE_FIELD = "file";
    static final String IDS_FIELD = "mediaIds";

    static final String UNSUPPORTED_FORMAT = "Format non accepté (JPEG, PNG, WebP, MP4 ou WebM).";
    static final String EMPTY_FILE = "Le fichier est vide.";
    static final String NOT_AVAILABLE = "Fichier introuvable ou déjà utilisé.";
    static final String POST_LIMIT = "6 fichiers maximum, dont une seule vidéo.";
    static final String COMMENT_LIMIT = "Un seul fichier par commentaire.";

    /** Unattached files are removed after this delay. */
    static final Duration ORPHAN_MAX_AGE = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository mediaRepository;
    private final MediaStorage storage;
    private final long maxImageBytes;
    private final long maxVideoBytes;

    public MediaService(
            MediaRepository mediaRepository,
            MediaStorage storage,
            @Value("${tounsiguide.media.max-image-bytes:10MB}") DataSize maxImageSize,
            @Value("${tounsiguide.media.max-video-bytes:50MB}") DataSize maxVideoSize
    ) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.maxImageBytes = maxImageSize.toBytes();
        this.maxVideoBytes = maxVideoSize.toBytes();
    }

    // ---------------------------------------------------------------- upload and read

    /** Checks the real format and the size of the file, stores it and records it as not attached yet. */
    @Transactional
    public Media upload(MultipartFile file, CurrentUser user) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException(FILE_FIELD, EMPTY_FILE);
        }
        MediaFormat format = detectFormat(file);
        checkSize(format.kind(), file.getSize());

        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(in, format.extension());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded file", exception);
        }
        try {
            return mediaRepository.save(new Media(format.kind(), format.contentType(), file.getSize(), key, user.id()));
        } catch (RuntimeException exception) {
            deleteFileQuietly(key);
            throw exception;
        }
    }

    /** Rejects a file whose size is over the limit of its kind. */
    void checkSize(MediaKind kind, long sizeBytes) {
        long max = kind == MediaKind.VIDEO ? maxVideoBytes : maxImageBytes;
        if (sizeBytes > max) {
            String what = kind == MediaKind.VIDEO ? "La vidéo" : "La photo";
            throw new MediaValidationException(FILE_FIELD, what + " dépasse " + megabytes(max) + " Mo.");
        }
    }

    private static MediaFormat detectFormat(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(MediaFormat.HEADER_LENGTH);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded file", exception);
        }
        return MediaFormat.detect(header)
                .orElseThrow(() -> new MediaValidationException(FILE_FIELD, UNSUPPORTED_FORMAT));
    }

    /** A media, or 404 when it does not exist or belongs to a deleted post (or to a comment of one). */
    @Transactional(readOnly = true)
    public Media getMedia(String mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: " + mediaId));
        if (media.isAttached() && mediaRepository.existsByIdAndPostStatus(mediaId, PublicationStatus.DELETED)) {
            throw new EntityNotFoundException("Media not found: " + mediaId);
        }
        return media;
    }

    public Resource loadFile(Media media) {
        return storage.load(media.getStorageKey());
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> mediaOfPost(Long postId) {
        return toResponses(mediaRepository.findAllByPostIdOrderByPositionAsc(postId));
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> mediaOfComment(Long commentId) {
        return toResponses(mediaRepository.findAllByCommentIdOrderByPositionAsc(commentId));
    }

    /** Media of many posts in one query, keyed by post id (posts without media are absent). */
    @Transactional(readOnly = true)
    public Map<Long, List<MediaResponse>> mediaOfPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return group(mediaRepository.findAllByPostIdInOrderByPositionAsc(postIds), Media::getPostId);
    }

    /** Media of many comments in one query, keyed by comment id (comments without media are absent). */
    @Transactional(readOnly = true)
    public Map<Long, List<MediaResponse>> mediaOfComments(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return group(mediaRepository.findAllByCommentIdInOrderByPositionAsc(commentIds), Media::getCommentId);
    }

    // ---------------------------------------------------------------- attach

    /** On creation: attaches the given files (null or empty = none). */
    @Transactional
    public void attachToPost(Long postId, List<String> mediaIds, CurrentUser user) {
        if (mediaIds != null && !mediaIds.isEmpty()) {
            replace(Target.post(postId), mediaIds, user);
        }
    }

    /** On update: null keeps the current files; a list (even empty) becomes the exact set and order. */
    @Transactional
    public void replacePostMedia(Long postId, List<String> mediaIds, CurrentUser user) {
        if (mediaIds != null) {
            replace(Target.post(postId), mediaIds, user);
        }
    }

    @Transactional
    public void attachToComment(Long commentId, List<String> mediaIds, CurrentUser user) {
        if (mediaIds != null && !mediaIds.isEmpty()) {
            replace(Target.comment(commentId), mediaIds, user);
        }
    }

    @Transactional
    public void replaceCommentMedia(Long commentId, List<String> mediaIds, CurrentUser user) {
        if (mediaIds != null) {
            replace(Target.comment(commentId), mediaIds, user);
        }
    }

    private void replace(Target target, List<String> requestedIds, CurrentUser user) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        if (ids.contains(null)) {
            throw new MediaValidationException(IDS_FIELD, NOT_AVAILABLE);
        }
        if (ids.size() > target.maxItems()) {
            throw new MediaValidationException(IDS_FIELD, target.limitMessage());
        }

        Map<String, Media> found = mediaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Media::getId, Function.identity()));
        List<Media> chosen = new ArrayList<>();
        for (String id : ids) {
            Media media = found.get(id);
            if (media == null || !user.id().equals(media.getOwnerId()) || !target.accepts(media)) {
                throw new MediaValidationException(IDS_FIELD, NOT_AVAILABLE);
            }
            chosen.add(media);
        }
        long videos = chosen.stream().filter(media -> media.getType() == MediaKind.VIDEO).count();
        if (videos > MAX_VIDEOS_PER_POST) {
            throw new MediaValidationException(IDS_FIELD, target.limitMessage());
        }

        List<Media> removed = target.currentMedia(mediaRepository).stream()
                .filter(media -> !ids.contains(media.getId()))
                .toList();
        for (int position = 0; position < chosen.size(); position++) {
            chosen.get(position).attach(target.postId(), target.commentId(), position);
        }
        mediaRepository.saveAll(chosen);
        delete(removed);
    }

    // ---------------------------------------------------------------- delete

    @Transactional
    public void deleteMediaOfComment(Long commentId) {
        if (commentId != null) {
            delete(mediaRepository.findAllByCommentIdOrderByPositionAsc(commentId));
        }
    }

    /**
     * Removes the files uploaded more than 24 hours ago and never attached. The media of a deleted post stay
     * attached to it (soft delete), so they are never removed here.
     */
    @Transactional
    public int deleteOrphans() {
        List<Media> orphans = mediaRepository
                .findAllByPostIdIsNullAndCommentIdIsNullAndCreatedAtBefore(Instant.now().minus(ORPHAN_MAX_AGE));
        delete(orphans);
        return orphans.size();
    }

    /** Deletes the rows now and the files once the transaction has committed (so a rollback keeps them). */
    private void delete(List<Media> media) {
        if (media.isEmpty()) {
            return;
        }
        mediaRepository.deleteAll(media);
        List<String> keys = media.stream().map(Media::getStorageKey).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(MediaService.this::deleteFileQuietly);
                }
            });
        } else {
            keys.forEach(this::deleteFileQuietly);
        }
    }

    private void deleteFileQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Could not delete media file {}: {}", key, exception.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<MediaResponse> toResponses(List<Media> media) {
        return media.stream().map(MediaResponse::of).toList();
    }

    private static Map<Long, List<MediaResponse>> group(List<Media> media, Function<Media, Long> key) {
        Map<Long, List<MediaResponse>> grouped = new LinkedHashMap<>();
        for (Media item : media) {
            grouped.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(MediaResponse.of(item));
        }
        return grouped;
    }

    private static String megabytes(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb * 1024 * 1024 == bytes ? Long.toString(mb) : String.format(java.util.Locale.FRANCE, "%.1f", bytes / (1024.0 * 1024));
    }

    /** What the files are attached to: one post or one comment. */
    private record Target(Long postId, Long commentId) {

        static Target post(Long postId) {
            return new Target(Objects.requireNonNull(postId), null);
        }

        static Target comment(Long commentId) {
            return new Target(null, Objects.requireNonNull(commentId));
        }

        boolean isPost() {
            return postId != null;
        }

        int maxItems() {
            return isPost() ? MAX_MEDIA_PER_POST : MAX_MEDIA_PER_COMMENT;
        }

        String limitMessage() {
            return isPost() ? POST_LIMIT : COMMENT_LIMIT;
        }

        /** Unattached, or already attached to this same post / comment. */
        boolean accepts(Media media) {
            return !media.isAttached()
                    || (Objects.equals(postId, media.getPostId()) && Objects.equals(commentId, media.getCommentId()));
        }

        List<Media> currentMedia(MediaRepository repository) {
            return isPost()
                    ? repository.findAllByPostIdOrderByPositionAsc(postId)
                    : repository.findAllByCommentIdOrderByPositionAsc(commentId);
        }
    }
}

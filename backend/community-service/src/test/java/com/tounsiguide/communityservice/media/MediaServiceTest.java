package com.tounsiguide.communityservice.media;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final CurrentUser SARRA = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));
    private static final CurrentUser ALI = new CurrentUser("user-2", "Ali", Set.of("MEMBER"));

    @Mock
    private MediaRepository mediaRepository;

    private final InMemoryMediaStorage storage = new InMemoryMediaStorage();

    private MediaService mediaService;

    /** What the mocked repository contains. */
    private final List<Media> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(mediaRepository, storage, DataSize.ofMegabytes(10), DataSize.ofMegabytes(50));
        lenient().when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            rows.add(media);
            return media;
        });
        lenient().when(mediaRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return rows.stream().filter(media -> ids.contains(media.getId())).toList();
        });
        lenient().when(mediaRepository.findAllByPostIdOrderByPositionAsc(any())).thenAnswer(invocation -> {
            Long postId = invocation.getArgument(0);
            return rows.stream().filter(media -> postId.equals(media.getPostId())).toList();
        });
        lenient().when(mediaRepository.findAllByCommentIdOrderByPositionAsc(any())).thenAnswer(invocation -> {
            Long commentId = invocation.getArgument(0);
            return rows.stream().filter(media -> commentId.equals(media.getCommentId())).toList();
        });
        lenient().when(mediaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Media uploaded(CurrentUser owner, MediaKind kind) {
        String key = storage.store(new java.io.ByteArrayInputStream(new byte[]{1}), ".bin");
        Media media = new Media(kind, kind == MediaKind.VIDEO ? "video/mp4" : "image/png", 1, key, owner.id());
        rows.add(media);
        return media;
    }

    private static List<String> ids(Media... media) {
        return Arrays.stream(media).map(Media::getId).toList();
    }

    // ---------------------------------------------------------------- upload

    @Test
    void uploadStoresAPhotoDetectedFromItsBytesAndRecordsTheUploader() {
        MockMultipartFile file = new MockMultipartFile("file", "holiday.txt", "text/plain", MediaFormatTest.PNG);

        Media media = mediaService.upload(file, SARRA);

        assertThat(media.getType()).isEqualTo(MediaKind.IMAGE);
        assertThat(media.getContentType()).isEqualTo("image/png");
        assertThat(media.getOwnerId()).isEqualTo("user-1");
        assertThat(media.isAttached()).isFalse();
        assertThat(media.getStorageKey()).endsWith(".png").doesNotContain("holiday");
        assertThat(storage.files).containsKey(media.getStorageKey());
    }

    @Test
    void uploadRejectsAFileThatIsNotAnAcceptedFormatWhateverItsName() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> mediaService.upload(file, SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.UNSUPPORTED_FORMAT)
                .extracting("field").isEqualTo("file");
        assertThat(storage.files).isEmpty();
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void uploadRejectsAnEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> mediaService.upload(file, SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.EMPTY_FILE);
    }

    @Test
    void aPhotoIsLimitedTo10MbAndAVideoTo50Mb() {
        long mb = 1024 * 1024;
        mediaService.checkSize(MediaKind.IMAGE, 10 * mb);
        mediaService.checkSize(MediaKind.VIDEO, 50 * mb);

        assertThatThrownBy(() -> mediaService.checkSize(MediaKind.IMAGE, 10 * mb + 1))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage("La photo dépasse 10 Mo.");
        assertThatThrownBy(() -> mediaService.checkSize(MediaKind.VIDEO, 50 * mb + 1))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage("La vidéo dépasse 50 Mo.");
    }

    @Test
    void theLimitsComeFromTheConfiguration() {
        MediaService small = new MediaService(mediaRepository, storage, DataSize.ofBytes(10), DataSize.ofBytes(20));
        byte[] tooBig = Arrays.copyOf(MediaFormatTest.JPEG, 11);

        assertThatThrownBy(() -> small.upload(new MockMultipartFile("file", "a.jpg", "image/jpeg", tooBig), SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageStartingWith("La photo dépasse");
        assertThat(storage.files).isEmpty();
    }

    // ---------------------------------------------------------------- attach to a post

    @Test
    void attachToPostKeepsTheRequestedOrder() {
        Media first = uploaded(SARRA, MediaKind.IMAGE);
        Media second = uploaded(SARRA, MediaKind.VIDEO);

        mediaService.attachToPost(5L, ids(second, first), SARRA);

        assertThat(second.getPostId()).isEqualTo(5L);
        assertThat(second.getPosition()).isZero();
        assertThat(first.getPostId()).isEqualTo(5L);
        assertThat(first.getPosition()).isEqualTo(1);
    }

    @Test
    void theMediaOfAnotherUserCannotBeAttached() {
        Media alisPhoto = uploaded(ALI, MediaKind.IMAGE);

        assertThatThrownBy(() -> mediaService.attachToPost(5L, ids(alisPhoto), SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.NOT_AVAILABLE)
                .extracting("field").isEqualTo("mediaIds");
        assertThat(alisPhoto.isAttached()).isFalse();
    }

    @Test
    void anUnknownIdOrAMediaUsedByAnotherPostIsRejected() {
        Media usedElsewhere = uploaded(SARRA, MediaKind.IMAGE);
        usedElsewhere.attach(9L, null, 0);

        assertThatThrownBy(() -> mediaService.attachToPost(5L, List.of("no-such-id"), SARRA))
                .hasMessage(MediaService.NOT_AVAILABLE);
        assertThatThrownBy(() -> mediaService.attachToPost(5L, ids(usedElsewhere), SARRA))
                .hasMessage(MediaService.NOT_AVAILABLE);
        assertThat(usedElsewhere.getPostId()).isEqualTo(9L);
    }

    @Test
    void aPostHasAtMostSixFiles() {
        Media[] seven = new Media[7];
        for (int i = 0; i < seven.length; i++) {
            seven[i] = uploaded(SARRA, MediaKind.IMAGE);
        }

        assertThatThrownBy(() -> mediaService.attachToPost(5L, ids(seven), SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.POST_LIMIT);

        mediaService.attachToPost(5L, ids(Arrays.copyOf(seven, 6)), SARRA);
        assertThat(seven[5].getPostId()).isEqualTo(5L);
    }

    @Test
    void aPostHasAtMostOneVideo() {
        Media firstVideo = uploaded(SARRA, MediaKind.VIDEO);
        Media secondVideo = uploaded(SARRA, MediaKind.VIDEO);

        assertThatThrownBy(() -> mediaService.attachToPost(5L, ids(firstVideo, secondVideo), SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.POST_LIMIT);
        assertThat(firstVideo.isAttached()).isFalse();
    }

    @Test
    void attachToPostWithoutIdsDoesNothing() {
        mediaService.attachToPost(5L, null, SARRA);
        mediaService.attachToPost(5L, List.of(), SARRA);

        verify(mediaRepository, never()).findAllById(any());
    }

    // ---------------------------------------------------------------- update of a post

    @Test
    void updatingWithNullKeepsTheCurrentMedia() {
        Media photo = uploaded(SARRA, MediaKind.IMAGE);
        mediaService.attachToPost(5L, ids(photo), SARRA);

        mediaService.replacePostMedia(5L, null, SARRA);

        assertThat(photo.getPostId()).isEqualTo(5L);
        verify(mediaRepository, never()).deleteAll(anyList());
        assertThat(storage.files).containsKey(photo.getStorageKey());
    }

    @Test
    void updatingWithAnEmptyListRemovesAndDeletesAllTheMedia() {
        Media photo = uploaded(SARRA, MediaKind.IMAGE);
        mediaService.attachToPost(5L, ids(photo), SARRA);

        mediaService.replacePostMedia(5L, List.of(), SARRA);

        verify(mediaRepository).deleteAll(List.of(photo));
        assertThat(storage.files).doesNotContainKey(photo.getStorageKey());
    }

    @Test
    void updatingWithAListBecomesTheExactSetAndOrder() {
        Media kept = uploaded(SARRA, MediaKind.IMAGE);
        Media removed = uploaded(SARRA, MediaKind.IMAGE);
        mediaService.attachToPost(5L, ids(kept, removed), SARRA);
        Media added = uploaded(SARRA, MediaKind.VIDEO);

        mediaService.replacePostMedia(5L, ids(added, kept), SARRA);

        assertThat(added.getPostId()).isEqualTo(5L);
        assertThat(added.getPosition()).isZero();
        assertThat(kept.getPosition()).isEqualTo(1);
        verify(mediaRepository).deleteAll(List.of(removed));
        assertThat(storage.files).doesNotContainKey(removed.getStorageKey()).containsKey(kept.getStorageKey());
    }

    // ---------------------------------------------------------------- comments and cleanup

    @Test
    void aCommentHasAtMostOneFile() {
        Media first = uploaded(SARRA, MediaKind.IMAGE);
        Media second = uploaded(SARRA, MediaKind.IMAGE);

        assertThatThrownBy(() -> mediaService.attachToComment(10L, ids(first, second), SARRA))
                .isInstanceOf(MediaValidationException.class)
                .hasMessage(MediaService.COMMENT_LIMIT);

        mediaService.attachToComment(10L, ids(first), SARRA);
        assertThat(first.getCommentId()).isEqualTo(10L);
        assertThat(first.getPostId()).isNull();
    }

    @Test
    void aMediaOfACommentCannotBeMovedToAPost() {
        Media photo = uploaded(SARRA, MediaKind.IMAGE);
        mediaService.attachToComment(10L, ids(photo), SARRA);

        assertThatThrownBy(() -> mediaService.attachToPost(5L, ids(photo), SARRA))
                .hasMessage(MediaService.NOT_AVAILABLE);
    }

    @Test
    void cleanupOnlyLooksForUnattachedFilesSoTheMediaOfADeletedPostAreKept() {
        Media ofDeletedPost = uploaded(SARRA, MediaKind.IMAGE);
        mediaService.attachToPost(5L, ids(ofDeletedPost), SARRA);
        when(mediaRepository.findAllByPostIdIsNullAndCommentIdIsNullAndCreatedAtBefore(any())).thenReturn(List.of());

        assertThat(mediaService.deleteOrphans()).isZero();

        verify(mediaRepository, never()).deleteAll(anyList());
        assertThat(storage.files).containsKey(ofDeletedPost.getStorageKey());
    }

    @Test
    void cleanupDeletesTheOldUnattachedFiles() {
        Media orphan = uploaded(SARRA, MediaKind.IMAGE);
        when(mediaRepository.findAllByPostIdIsNullAndCommentIdIsNullAndCreatedAtBefore(any()))
                .thenReturn(List.of(orphan));

        assertThat(mediaService.deleteOrphans()).isEqualTo(1);

        verify(mediaRepository).deleteAll(List.of(orphan));
        assertThat(storage.files).doesNotContainKey(orphan.getStorageKey());
    }
}

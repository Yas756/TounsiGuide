package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.location.LocationClient;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.topic.TopicModuleService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final LocationClient locationClient;
    private final PostChangeNotifier changeNotifier;
    private final MediaService mediaService;
    private final TopicModuleService moduleService;

    public PostService(
            PostRepository postRepository,
            LocationClient locationClient,
            PostChangeNotifier changeNotifier,
            MediaService mediaService,
            TopicModuleService moduleService
    ) {
        this.postRepository = postRepository;
        this.locationClient = locationClient;
        this.changeNotifier = changeNotifier;
        this.mediaService = mediaService;
        this.moduleService = moduleService;
    }

    @Transactional
    public Post createPost(String content, Long locationId, CurrentUser user) {
        return createPost(content, locationId, null, user);
    }

    /** @param mediaIds ids from POST /api/v1/media, in display order (null or empty = none) */
    @Transactional
    public Post createPost(String content, Long locationId, List<String> mediaIds, CurrentUser user) {
        return createPost(content, locationId, null, mediaIds, user);
    }

    /** @param moduleId optional topic module; it must be APPROVED (400 {@code details.moduleId} otherwise) */
    @Transactional
    public Post createPost(String content, Long locationId, Long moduleId, List<String> mediaIds, CurrentUser user) {
        locationClient.requireExists(locationId);
        if (moduleId != null) {
            moduleService.requireSelectable(moduleId);
        }
        Post toSave = new Post(content, user.id(), user.displayName(), locationId);
        toSave.setModuleId(moduleId);
        Post post = postRepository.save(toSave);
        mediaService.attachToPost(post.getId(), mediaIds, user);
        changeNotifier.postChanged(post.getId());
        return post;
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /** Length of the {@code q} text of the unified search (S3-US10). */
    public static final int MIN_SEARCH_LENGTH = 2;
    public static final int MAX_SEARCH_LENGTH = 100;

    /** Newest first; the id breaks ties so that a page never repeats or skips a post with the same date. */
    static final Sort FEED_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /**
     * One page of the feed: published posts only (deleted ones are left out), newest first.
     *
     * @throws InvalidParameterException when {@code page < 0} or {@code size} is not between 1 and 50
     */
    @Transactional(readOnly = true)
    public Page<Post> getPublishedPosts(int page, int size) {
        return getPublishedPosts(page, size, null);
    }

    /**
     * Same as {@link #getPublishedPosts(int, int)}, limited to one topic module when {@code moduleId} is not null.
     */
    @Transactional(readOnly = true)
    public Page<Post> getPublishedPosts(int page, int size, Long moduleId) {
        PageRequest request = feedPage(page, size);
        return moduleId == null
                ? postRepository.findAllByPublicationStatus(PublicationStatus.PUBLISHED, request)
                : postRepository.findAllByPublicationStatusAndModuleId(PublicationStatus.PUBLISHED, moduleId, request);
    }

    /**
     * Unified search (S3-US10): published posts whose text or author name contains {@code query}
     * (2 to 100 characters, case and accent insensitive), newest first, optionally within one topic module.
     * Deleted and restricted posts are never found, like in the feed.
     *
     * @throws InvalidParameterException {@code q} when the text is shorter than 2 or longer than 100 characters
     */
    @Transactional(readOnly = true)
    public Page<Post> searchPublishedPosts(String query, int page, int size, Long moduleId) {
        String text = query == null ? "" : query.trim();
        if (text.length() < MIN_SEARCH_LENGTH || text.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidParameterException("q", "q must be between " + MIN_SEARCH_LENGTH + " and "
                    + MAX_SEARCH_LENGTH + " characters");
        }
        String pattern = "%" + text.toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return postRepository.search(PublicationStatus.PUBLISHED, moduleId, pattern, feedPage(page, size));
    }

    private PageRequest feedPage(int page, int size) {
        if (page < 0) {
            throw new InvalidParameterException("page", "page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("size", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, FEED_ORDER);
    }

    /** Most ids accepted by {@link #getPublishedPostsByIds(List)} in one call. */
    public static final int MAX_BATCH_SIZE = 50;

    /**
     * The published posts among these ids, in the order of the ids (unknown and deleted posts are left out).
     * Used by the Business Service to build a page wall without copying posts (Sprint 3, S3-B04).
     *
     * @throws InvalidParameterException when more than 50 ids are asked
     */
    @Transactional(readOnly = true)
    public List<Post> getPublishedPostsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new InvalidParameterException("ids", "ids must contain at most " + MAX_BATCH_SIZE + " values");
        }
        java.util.Map<Long, Post> byId = new java.util.HashMap<>();
        postRepository.findAllByIdInAndPublicationStatus(distinct, PublicationStatus.PUBLISHED)
                .forEach(post -> byId.put(post.getId(), post));
        return distinct.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    /** A published post; 404 when it does not exist or was deleted. */
    @Transactional(readOnly = true)
    public Post getPostById(Long postId) {
        return getPostById(postId, false);
    }

    /** @param includeDeleted true for an administrator, who may still read a deleted or restricted post */
    @Transactional(readOnly = true)
    public Post getPostById(Long postId, boolean includeDeleted) {
        return getPostById(postId, includeDeleted, null);
    }

    /**
     * One post as a given reader may see it: an administrator reads everything; a DELETED post answers 404 to
     * everyone else; a RESTRICTED post (moderation, S3-B07) answers 404 except to its author, who sees it with
     * its status so the interface can show a notice.
     *
     * @param viewerId the caller's user id, or null for a visitor
     */
    @Transactional(readOnly = true)
    public Post getPostById(Long postId, boolean admin, String viewerId) {
        Post post = findAnyPostOrThrow(postId);
        if (admin) {
            return post;
        }
        if (post.isDeleted() || (post.isRestricted() && !post.getAuthorId().equals(viewerId))) {
            throw notFound(postId);
        }
        return post;
    }

    /** Replaces the content and the Location (null removes it), keeping the media. Author only. */
    @Transactional
    public Post updatePost(Long postId, String content, Long locationId, CurrentUser user) {
        return updatePost(postId, content, locationId, null, user);
    }

    /**
     * Replaces the content and the Location (null removes it). Author only.
     *
     * @param mediaIds null keeps the current media; a list (even empty) becomes the exact set and order
     */
    @Transactional
    public Post updatePost(Long postId, String content, Long locationId, List<String> mediaIds, CurrentUser user) {
        return updatePost(postId, content, locationId, findPostOrThrow(postId).getModuleId(), mediaIds, user);
    }

    /**
     * @param moduleId the module after the edit (null removes it). A new module must be APPROVED; keeping a module
     *                 that was archived in the meantime is allowed.
     */
    @Transactional
    public Post updatePost(Long postId, String content, Long locationId, Long moduleId, List<String> mediaIds,
                           CurrentUser user) {
        Post post = findPostOrThrow(postId);
        user.requireAuthor(post.getAuthorId());
        if (!Objects.equals(locationId, post.getLocationId())) {
            locationClient.requireExists(locationId);
        }
        if (moduleId != null && !Objects.equals(moduleId, post.getModuleId())) {
            moduleService.requireSelectable(moduleId);
        }
        post.setContent(content);
        post.setLocationId(locationId);
        post.setModuleId(moduleId);
        Post saved = postRepository.save(post);
        mediaService.replacePostMedia(postId, mediaIds, user);
        changeNotifier.postChanged(postId);
        return saved;
    }

    /**
     * Soft delete (S2-B04): the author or an administrator marks the post DELETED. Its comments and their
     * photos/videos are kept (moderation history); everything about the post then answers 404, except for
     * administrators. Deleting it again answers 404.
     */
    @Transactional
    public void deletePost(Long postId, CurrentUser user) {
        Post post = findAnyPostOrThrow(postId);
        if (post.isDeleted()) {
            throw notFound(postId);
        }
        user.requireAuthorOrAdmin(post.getAuthorId());
        post.markDeleted(user.id());
        postRepository.save(post);
        changeNotifier.postChanged(postId);
    }

    /** A published post, or 404 (also for a deleted or restricted one: a restricted post cannot be edited). */
    private Post findPostOrThrow(Long postId) {
        Post post = findAnyPostOrThrow(postId);
        if (post.isDeleted() || post.isRestricted()) {
            throw notFound(postId);
        }
        return post;
    }

    private Post findAnyPostOrThrow(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> notFound(postId));
    }

    private static EntityNotFoundException notFound(Long postId) {
        return new EntityNotFoundException("Post not found: " + postId);
    }
}

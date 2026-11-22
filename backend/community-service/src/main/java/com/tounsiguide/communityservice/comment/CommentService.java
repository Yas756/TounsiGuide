package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostChangeNotifier changeNotifier;
    private final MediaService mediaService;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            PostChangeNotifier changeNotifier,
            MediaService mediaService
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.changeNotifier = changeNotifier;
        this.mediaService = mediaService;
    }

    @Transactional
    public Comment createComment(Long postId, String content, CurrentUser user) {
        return createComment(postId, content, null, user);
    }

    /** @param mediaIds id from POST /api/v1/media (one at most; null or empty = none) */
    @Transactional
    public Comment createComment(Long postId, String content, List<String> mediaIds, CurrentUser user) {
        Post post = findPostOrThrow(postId);
        Comment comment = commentRepository.save(new Comment(post, content, user.id(), user.displayName()));
        mediaService.attachToComment(comment.getId(), mediaIds, user);
        changeNotifier.postChanged(postId);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<Comment> getAllCommentsForPost(Long postId) {
        return getAllCommentsForPost(postId, false);
    }

    /** @param includeDeleted true for an administrator, who may still list the comments of a deleted post */
    @Transactional(readOnly = true)
    public List<Comment> getAllCommentsForPost(Long postId, boolean includeDeleted) {
        return getAllCommentsForPost(postId, includeDeleted, null);
    }

    /**
     * The comments a reader may see. An administrator sees everything. Others get 404 for a deleted post, and for a
     * restricted post unless they wrote it; restricted comments (S3-B07) are left out except the reader's own.
     */
    @Transactional(readOnly = true)
    public List<Comment> getAllCommentsForPost(Long postId, boolean admin, String viewerId) {
        Post post = findAnyPostOrThrow(postId);
        if (admin) {
            return commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
        }
        if (post.isDeleted() || (post.isRestricted() && !post.getAuthorId().equals(viewerId))) {
            throw postNotFound(postId);
        }
        return commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(comment -> !comment.isRestricted() || comment.getAuthorId().equals(viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Comment getCommentById(Long postId, Long commentId) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        if (comment.isRestricted()) {
            throw new EntityNotFoundException("Comment not found: " + commentId);
        }
        return comment;
    }

    @Transactional
    public Comment updateComment(Long postId, Long commentId, String content, CurrentUser user) {
        return updateComment(postId, commentId, content, null, user);
    }

    /** @param mediaIds null keeps the current media; a list (even empty) replaces it */
    @Transactional
    public Comment updateComment(Long postId, Long commentId, String content, List<String> mediaIds, CurrentUser user) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        user.requireAuthor(comment.getAuthorId());
        comment.setContent(content);
        Comment saved = commentRepository.save(comment);
        mediaService.replaceCommentMedia(commentId, mediaIds, user);
        changeNotifier.postChanged(postId);
        return saved;
    }

    /** Unchanged by the soft delete: the author or an admin may still remove a comment of a deleted post. */
    @Transactional
    public void deleteComment(Long postId, Long commentId, CurrentUser user) {
        findAnyPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        user.requireAuthorOrAdmin(comment.getAuthorId());
        mediaService.deleteMediaOfComment(comment.getId());
        commentRepository.delete(comment);
        changeNotifier.postChanged(postId);
    }

    /** A published post: the comments of a deleted or restricted post answer 404 (S2-B04, S3-B07). */
    private Post findPostOrThrow(Long postId) {
        Post post = findAnyPostOrThrow(postId);
        if (post.isDeleted() || post.isRestricted()) {
            throw postNotFound(postId);
        }
        return post;
    }

    private Post findAnyPostOrThrow(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> postNotFound(postId));
    }

    private static EntityNotFoundException postNotFound(Long postId) {
        return new EntityNotFoundException("Post not found: " + postId);
    }

    private Comment findCommentForPostOrThrow(Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));

        if (!postId.equals(comment.getPost().getId())) {
            throw new EntityNotFoundException("Comment not found: " + commentId);
        }

        return comment;
    }
}

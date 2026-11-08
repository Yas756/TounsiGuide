package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.common.security.CurrentUser;
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

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    @Transactional
    public Comment createComment(Long postId, String content, CurrentUser user) {
        Post post = findPostOrThrow(postId);
        Comment comment = new Comment(post, content, user.id(), user.displayName());
        return commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public List<Comment> getAllCommentsForPost(Long postId) {
        findPostOrThrow(postId);
        return commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
    }

    @Transactional(readOnly = true)
    public Comment getCommentById(Long postId, Long commentId) {
        findPostOrThrow(postId);
        return findCommentForPostOrThrow(postId, commentId);
    }

    @Transactional
    public Comment updateComment(Long postId, Long commentId, String content, CurrentUser user) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        user.requireAuthor(comment.getAuthorId());
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, CurrentUser user) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        user.requireAuthorOrAdmin(comment.getAuthorId());
        commentRepository.delete(comment);
    }

    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
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

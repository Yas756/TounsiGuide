package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PostService;
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
    public Comment createComment(Long postId, String content) {
        Post post = findPostOrThrow(postId);
        Comment comment = new Comment(
                post,
                content,
                PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME
        );
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
    public Comment updateComment(Long postId, Long commentId, String content) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        findPostOrThrow(postId);
        Comment comment = findCommentForPostOrThrow(postId, commentId);
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

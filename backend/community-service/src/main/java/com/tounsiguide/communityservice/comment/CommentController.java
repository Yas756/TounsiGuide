package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.comment.dto.CreateCommentRequest;
import com.tounsiguide.communityservice.comment.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;

    public CommentController(CommentService commentService, CommentMapper commentMapper) {
        this.commentService = commentService;
        this.commentMapper = commentMapper;
    }

    @GetMapping
    public List<CommentResponse> getAllComments(@PathVariable Long postId) {
        return commentService.getAllCommentsForPost(postId).stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{commentId}")
    public CommentResponse getCommentById(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return commentMapper.toResponse(commentService.getCommentById(postId, commentId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        Comment comment = commentService.createComment(postId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toResponse(comment));
    }

    @PutMapping("/{commentId}")
    public CommentResponse updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        Comment comment = commentService.updateComment(postId, commentId, request.content());
        return commentMapper.toResponse(comment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(postId, commentId);
        return ResponseEntity.noContent().build();
    }
}

package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.comment.dto.CreateCommentRequest;
import com.tounsiguide.communityservice.comment.dto.UpdateCommentRequest;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;
    private final MediaService mediaService;

    public CommentController(CommentService commentService, CommentMapper commentMapper, MediaService mediaService) {
        this.commentService = commentService;
        this.commentMapper = commentMapper;
        this.mediaService = mediaService;
    }

    /** 404 when the post was deleted, except to an administrator. */
    @GetMapping
    public List<CommentResponse> getAllComments(
            @PathVariable Long postId,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId
    ) {
        List<Comment> comments = commentService.getAllCommentsForPost(postId, CurrentUser.isAdminRoles(roles), userId);
        Map<Long, List<MediaResponse>> media =
                mediaService.mediaOfComments(comments.stream().map(Comment::getId).toList());
        return comments.stream()
                .map(comment -> commentMapper.toResponse(comment, media.getOrDefault(comment.getId(), List.of())))
                .toList();
    }

    @GetMapping("/{commentId}")
    public CommentResponse getCommentById(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return withMedia(commentService.getCommentById(postId, commentId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request,
            CurrentUser user
    ) {
        Comment comment = commentService.createComment(postId, request.content(), request.mediaIds(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(withMedia(comment));
    }

    @PutMapping("/{commentId}")
    public CommentResponse updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            CurrentUser user
    ) {
        Comment comment = commentService.updateComment(
                postId, commentId, request.content(), request.mediaIds(), user);
        return withMedia(comment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            CurrentUser user
    ) {
        commentService.deleteComment(postId, commentId, user);
        return ResponseEntity.noContent().build();
    }

    private CommentResponse withMedia(Comment comment) {
        return commentMapper.toResponse(comment, mediaService.mediaOfComment(comment.getId()));
    }
}

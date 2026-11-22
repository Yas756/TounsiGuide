package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommentMapper {

    /** A comment without photo or video. */
    public CommentResponse toResponse(Comment comment) {
        return toResponse(comment, List.of());
    }

    public CommentResponse toResponse(Comment comment, List<MediaResponse> media) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getContent(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                media == null ? List.of() : media,
                comment.getPublicationStatus()
        );
    }
}

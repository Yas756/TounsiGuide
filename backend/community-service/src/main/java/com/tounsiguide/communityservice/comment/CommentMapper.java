package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getContent(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}

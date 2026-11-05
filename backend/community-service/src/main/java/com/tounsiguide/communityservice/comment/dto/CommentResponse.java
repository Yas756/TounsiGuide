package com.tounsiguide.communityservice.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long postId,
        String content,
        String authorId,
        String authorName,
        Instant createdAt,
        Instant updatedAt
) {
}

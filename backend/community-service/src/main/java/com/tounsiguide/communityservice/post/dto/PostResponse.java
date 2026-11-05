package com.tounsiguide.communityservice.post.dto;

import java.time.Instant;

public record PostResponse(
        Long id,
        String content,
        String authorId,
        String authorName,
        Long locationId,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.tounsiguide.communityservice.post.dto;

import java.time.Instant;

public record PostResponse(
        Long id,
        String content,
        String authorId,
        String authorName,
        Long locationId,
        PlaceDto place,
        Instant createdAt,
        Instant updatedAt
) {

    /** Response of a post without a map place (used by the existing tests). */
    public PostResponse(
            Long id,
            String content,
            String authorId,
            String authorName,
            Long locationId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, content, authorId, authorName, locationId, null, createdAt, updatedAt);
    }
}
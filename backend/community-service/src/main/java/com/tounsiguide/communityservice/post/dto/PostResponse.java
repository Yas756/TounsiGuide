package com.tounsiguide.communityservice.post.dto;

import java.time.Instant;

/**
 * A post. {@code locationId} refers to the Location &amp; Matching Service; the frontend loads the
 * place details from GET /api/v1/locations?ids=... (this service does not copy them).
 */
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

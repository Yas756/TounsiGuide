package com.tounsiguide.communityservice.post.dto;

import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.PublicationStatus;

import java.time.Instant;
import java.util.List;

/**
 * A post. {@code locationId} refers to the Location &amp; Matching Service; the frontend loads the
 * place details from GET /api/v1/locations?ids=... (this service does not copy them).
 * {@code media} lists the photos and videos in display order (never null).
 * {@code publicationStatus} is PUBLISHED, or DELETED (only an administrator can read a deleted post).
 */
public record PostResponse(
        Long id,
        String content,
        String authorId,
        String authorName,
        Long locationId,
        Instant createdAt,
        Instant updatedAt,
        PublicationStatus publicationStatus,
        List<MediaResponse> media,
        /** Topic module of the post (S3-B08), only when it is APPROVED; otherwise the three fields are null. */
        Long moduleId,
        String moduleSlug,
        String moduleName
) {

    /** A post without topic module. */
    public PostResponse(Long id, String content, String authorId, String authorName, Long locationId, Instant createdAt,
                        Instant updatedAt, PublicationStatus publicationStatus, List<MediaResponse> media) {
        this(id, content, authorId, authorName, locationId, createdAt, updatedAt, publicationStatus, media, null, null, null);
    }
}

package com.tounsiguide.communityservice.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param locationId optional id of a Location from the Location &amp; Matching Service
 *                   (the place picker gets it from POST /api/v1/locations/resolve)
 * @param mediaIds   optional ids returned by POST /api/v1/media, in display order (6 at most, one video at most)
 */
public record CreatePostRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 2_000, message = "Content must not exceed 2000 characters")
        String content,

        @Positive(message = "Location id must be positive")
        Long locationId,

        List<String> mediaIds,

        /** Optional APPROVED topic module (S3-B08); null = none (on update: removes it). */
        @Positive(message = "Module id must be positive")
        Long moduleId
) {

    /** Without module (older clients and tests). */
    public CreatePostRequest(String content, Long locationId, List<String> mediaIds) {
        this(content, locationId, mediaIds, null);
    }
}

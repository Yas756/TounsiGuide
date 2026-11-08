package com.tounsiguide.communityservice.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param locationId optional id of a Location from the Location &amp; Matching Service
 *                   (the place picker gets it from POST /api/v1/locations/resolve)
 */
public record CreatePostRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 2_000, message = "Content must not exceed 2000 characters")
        String content,

        @Positive(message = "Location id must be positive")
        Long locationId
) {
}

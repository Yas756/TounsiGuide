package com.tounsiguide.communityservice.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces the content and the Location of the post (null removes the Location).
 *
 * @param mediaIds null keeps the current photos/videos; a list (even empty) becomes the exact set and order,
 *                 and the files left out are deleted
 */
public record UpdatePostRequest(
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
    public UpdatePostRequest(String content, Long locationId, List<String> mediaIds) {
        this(content, locationId, mediaIds, null);
    }
}

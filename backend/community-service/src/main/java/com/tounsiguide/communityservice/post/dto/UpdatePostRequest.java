package com.tounsiguide.communityservice.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Replaces the content and the Location of the post (null removes the Location). */
public record UpdatePostRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 2_000, message = "Content must not exceed 2000 characters")
        String content,

        @Positive(message = "Location id must be positive")
        Long locationId
) {
}

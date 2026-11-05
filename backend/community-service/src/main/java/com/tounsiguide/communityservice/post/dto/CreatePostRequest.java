package com.tounsiguide.communityservice.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 2_000, message = "Content must not exceed 2000 characters")
        String content,
        Long locationId
) {
}

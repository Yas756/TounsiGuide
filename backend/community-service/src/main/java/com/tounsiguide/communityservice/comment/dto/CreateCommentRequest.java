package com.tounsiguide.communityservice.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 1_000, message = "Content must not exceed 1000 characters")
        String content
) {
}

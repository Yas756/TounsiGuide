package com.tounsiguide.communityservice.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCommentRequest(
        @NotBlank(message = "Content must not be blank")
        @Size(max = 1_000, message = "Content must not exceed 1000 characters")
        String content,

        /** Ids from POST /api/v1/media (one at most). On update, null keeps the current one. */
        List<String> mediaIds
) {
}

package com.tounsiguide.businessservice.wall.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/business-pages/{id}/wall/{postId}/hide. */
public record HideRequest(
        @Size(max = 500, message = "500 caractères au maximum")
        String reason
) {
}

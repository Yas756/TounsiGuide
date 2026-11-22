package com.tounsiguide.locationservice.association.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Administrator sets the place of a post directly (correction). */
public record AdminSetAssociationRequest(
        @NotNull(message = "locationId is required")
        @Positive(message = "locationId must be positive")
        Long locationId,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {
}

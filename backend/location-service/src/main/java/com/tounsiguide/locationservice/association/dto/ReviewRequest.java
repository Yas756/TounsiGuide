package com.tounsiguide.locationservice.association.dto;

import com.tounsiguide.locationservice.association.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Administrator decision on an association (S2-US08). The reason is required. */
public record ReviewRequest(
        @NotNull(message = "decision is required")
        ReviewDecision decision,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {
}

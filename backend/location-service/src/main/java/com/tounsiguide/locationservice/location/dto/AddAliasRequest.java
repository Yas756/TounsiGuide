package com.tounsiguide.locationservice.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/locations/{id}/aliases (administrator). */
public record AddAliasRequest(
        @NotBlank(message = "Alias must not be blank")
        @Size(max = 150, message = "Alias must not exceed 150 characters")
        String alias
) {
}

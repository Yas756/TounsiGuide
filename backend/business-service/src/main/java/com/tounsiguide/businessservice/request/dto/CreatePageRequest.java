package com.tounsiguide.businessservice.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/business-pages/requests. */
public record CreatePageRequest(
        @NotNull(message = "Le lieu est obligatoire")
        @Positive(message = "Identifiant de lieu invalide")
        Long locationId,

        @Size(max = 120, message = "120 caractères au maximum")
        String proposedName,

        @NotBlank(message = "La preuve d'autorité est obligatoire")
        @Size(max = 2000, message = "2000 caractères au maximum")
        String evidence,

        @Size(max = 255, message = "255 caractères au maximum")
        String contact
) {
}

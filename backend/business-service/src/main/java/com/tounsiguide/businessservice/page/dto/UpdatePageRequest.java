package com.tounsiguide.businessservice.page.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/business-pages/{id}: the whole official information (a missing or empty field is cleared,
 * except the name, which is required).
 */
public record UpdatePageRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120, message = "120 caractères au maximum")
        String name,

        @Size(max = 2000, message = "2000 caractères au maximum")
        String description,

        @Size(max = 500, message = "500 caractères au maximum")
        String openingHours,

        @Size(max = 40, message = "40 caractères au maximum")
        @Pattern(regexp = "^$|^[0-9+()./ -]{4,40}$", message = "Numéro de téléphone invalide")
        String phone,

        @Size(max = 254, message = "254 caractères au maximum")
        @Email(message = "Adresse e-mail invalide")
        String email,

        @Size(max = 255, message = "255 caractères au maximum")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Le site web doit commencer par http:// ou https://")
        String website,

        @Size(max = 255, message = "255 caractères au maximum")
        String address
) {
}

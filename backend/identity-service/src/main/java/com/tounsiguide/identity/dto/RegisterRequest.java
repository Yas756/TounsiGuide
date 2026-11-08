package com.tounsiguide.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        @Size(max = 255, message = "Email trop long")
        String email,

        @NotBlank(message = "Le nom affiché est obligatoire")
        @Size(min = 2, max = 60, message = "Le nom affiché doit contenir entre 2 et 60 caractères")
        String displayName,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
        String password) {
}

package com.tounsiguide.businessservice.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/business-pages/requests/{id}/reject: the reason is shown to the requester. */
public record RejectRequest(
        @NotBlank(message = "Le motif du refus est obligatoire")
        @Size(max = 1000, message = "1000 caractères au maximum")
        String reason
) {
}

package com.tounsiguide.locationservice.association.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of clarify and dispute: the note is required. */
public record NoteRequest(
        @NotBlank(message = "note is required")
        @Size(max = 500, message = "note must be at most 500 characters")
        String note
) {
}

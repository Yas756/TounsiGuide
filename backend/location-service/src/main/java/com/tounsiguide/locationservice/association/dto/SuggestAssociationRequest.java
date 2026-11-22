package com.tounsiguide.locationservice.association.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A member suggests a place for a post (S2-US04). {@code commentId} when the suggestion comes from a comment. */
public record SuggestAssociationRequest(
        @NotNull(message = "locationId is required")
        @Positive(message = "locationId must be positive")
        Long locationId,

        @Size(max = 500, message = "note must be at most 500 characters")
        String note,

        @Positive(message = "commentId must be positive")
        Long commentId
) {
}

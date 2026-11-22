package com.tounsiguide.locationservice.association.dto;

import com.tounsiguide.locationservice.association.Status;

import java.time.Instant;

/** One line of the decision history. {@code fromStatus} is null for the creation. */
public record DecisionResponse(
        Long id,
        Status fromStatus,
        Status toStatus,
        String actorId,
        String actorRole,
        String reason,
        Instant createdAt
) {
}

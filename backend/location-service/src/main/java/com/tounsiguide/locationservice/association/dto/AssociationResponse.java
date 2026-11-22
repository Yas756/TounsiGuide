package com.tounsiguide.locationservice.association.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tounsiguide.locationservice.association.Direction;
import com.tounsiguide.locationservice.association.EvidenceType;
import com.tounsiguide.locationservice.association.Origin;
import com.tounsiguide.locationservice.association.Status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * An association as returned by the API (association-api.md, section 6).
 * {@code history} is only filled by {@code GET /api/v1/associations/{id}}.
 * {@code postDeleted} is true once the post was deleted in the Community Service (the association is kept as it was).
 */
public record AssociationResponse(
        Long id,
        Long postId,
        LocationRef location,
        Origin origin,
        Status status,
        int score,
        boolean forced,
        Map<String, Object> analysis,
        List<EvidenceResponse> evidence,
        String createdBy,
        Instant createdAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        boolean postDeleted,
        Instant postDeletedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<DecisionResponse> history
) {

    public record LocationRef(Long id, String name, String city) {
    }

    public record EvidenceResponse(
            Long id,
            EvidenceType type,
            Direction direction,
            int weight,
            String actorId,
            String actorName,
            Long commentId,
            String note,
            Instant createdAt
    ) {
    }
}

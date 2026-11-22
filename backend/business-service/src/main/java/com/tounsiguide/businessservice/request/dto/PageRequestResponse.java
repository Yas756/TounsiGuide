package com.tounsiguide.businessservice.request.dto;

import com.tounsiguide.businessservice.request.RequestStatus;
import com.tounsiguide.businessservice.request.VerificationRequest;

import java.time.Instant;

/**
 * A page request. {@code locationName}/{@code locationCity} come from the Location Service and are null when it
 * cannot be reached (the list still answers).
 */
public record PageRequestResponse(
        Long id,
        Long locationId,
        String locationName,
        String locationCity,
        String requesterId,
        String requesterName,
        String proposedName,
        String evidence,
        String contact,
        RequestStatus status,
        Instant createdAt,
        String decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        Long pageId
) {

    public static PageRequestResponse from(VerificationRequest request, String locationName, String locationCity) {
        return new PageRequestResponse(request.getId(), request.getLocationId(), locationName, locationCity,
                request.getRequesterId(), request.getRequesterName(), request.getProposedName(), request.getEvidence(),
                request.getContact(), request.getStatus(), request.getCreatedAt(), request.getDecidedBy(),
                request.getDecidedByName(), request.getDecidedAt(), request.getDecisionReason(), request.getPageId());
    }
}

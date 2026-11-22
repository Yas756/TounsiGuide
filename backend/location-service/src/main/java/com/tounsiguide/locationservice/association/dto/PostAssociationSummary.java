package com.tounsiguide.locationservice.association.dto;

/** Feed summary of one post: its confirmed place (or null) and how many proposals are open. */
public record PostAssociationSummary(Confirmed confirmed, int openProposals) {

    public record Confirmed(Long associationId, Long locationId, int score) {
    }
}

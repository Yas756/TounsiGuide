package com.tounsiguide.businessservice.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** A representative's request for the page of an existing Location, with proof of authority (S3-US01). */
@Entity
@Table(name = "verification_request", indexes = {
        @Index(name = "idx_request_status", columnList = "status"),
        @Index(name = "idx_request_requester", columnList = "requester_id"),
        @Index(name = "idx_request_location", columnList = "location_id")
})
public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "requester_name", length = 60)
    private String requesterName;

    @Column(name = "proposed_name", length = 120)
    private String proposedName;

    @Column(nullable = false, length = 2000)
    private String evidence;

    @Column(length = 255)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_by_name", length = 60)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    /** The page created when the request was approved. */
    @Column(name = "page_id")
    private Long pageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationRequest() {
    }

    public VerificationRequest(Long locationId, String requesterId, String requesterName, String proposedName,
                               String evidence, String contact) {
        this.locationId = locationId;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.proposedName = proposedName;
        this.evidence = evidence;
        this.contact = contact;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }

    public void approve(String adminId, String adminName, Long pageId) {
        decide(RequestStatus.APPROVED, adminId, adminName, null);
        this.pageId = pageId;
    }

    public void reject(String adminId, String adminName, String reason) {
        decide(RequestStatus.REJECTED, adminId, adminName, reason);
    }

    private void decide(RequestStatus newStatus, String adminId, String adminName, String reason) {
        this.status = newStatus;
        this.decidedBy = adminId;
        this.decidedByName = adminName;
        this.decidedAt = Instant.now();
        this.decisionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getLocationId() {
        return locationId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getProposedName() {
        return proposedName;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getContact() {
        return contact;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecidedByName() {
        return decidedByName;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Long getPageId() {
        return pageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

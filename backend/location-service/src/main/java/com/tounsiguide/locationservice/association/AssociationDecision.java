package com.tounsiguide.locationservice.association;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the decision history of an association (append-only, FR-15 auditability). */
@Entity
@Table(name = "association_decisions", indexes = {
        @Index(name = "idx_association_decisions_association", columnList = "association_id")
})
public class AssociationDecision {

    /** Values of {@code actorRole}. */
    public static final String ROLE_AUTHOR = "AUTHOR";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SYSTEM = "SYSTEM";

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private Association association;

    /** Null for the creation of the association. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private Status toStatus;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssociationDecision() {
        // Required by JPA.
    }

    AssociationDecision(Association association, Status fromStatus, Status toStatus, String actorId,
                        String actorRole, String reason) {
        this.association = association;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.reason = AssociationEvidence.truncate(reason, MAX_REASON_LENGTH);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Association getAssociation() {
        return association;
    }

    public Status getFromStatus() {
        return fromStatus;
    }

    public Status getToStatus() {
        return toStatus;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

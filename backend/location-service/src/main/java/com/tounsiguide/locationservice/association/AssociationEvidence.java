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

/**
 * One signal for or against an association (append-only). The weight is copied from the configuration
 * when the evidence is created, so an old score can still be explained after the weights change.
 */
@Entity
@Table(name = "association_evidence", indexes = {
        @Index(name = "idx_association_evidence_association", columnList = "association_id")
})
public class AssociationEvidence {

    public static final int MAX_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private Association association;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private EvidenceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "weight", nullable = false)
    private int weight;

    /** Null for evidence created by the system. */
    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "note", length = MAX_NOTE_LENGTH)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssociationEvidence() {
        // Required by JPA.
    }

    AssociationEvidence(Association association, EvidenceType type, int weight, String actorId, String actorName,
                        Long commentId, String note) {
        this.association = association;
        this.type = type;
        this.direction = type.direction();
        this.weight = weight;
        this.actorId = actorId;
        this.actorName = actorName;
        this.commentId = commentId;
        this.note = truncate(note, MAX_NOTE_LENGTH);
        this.createdAt = Instant.now();
    }

    static String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Association getAssociation() {
        return association;
    }

    public EvidenceType getType() {
        return type;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getWeight() {
        return weight;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public Long getCommentId() {
        return commentId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

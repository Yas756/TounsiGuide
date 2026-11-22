package com.tounsiguide.communityservice.report;

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

/** Append-only history of moderation decisions (FR-15): who decided what on which content, when and why. */
@Entity
@Table(name = "moderation_decisions", indexes = @Index(name = "idx_moderation_target", columnList = "target_type, target_id"))
public class ModerationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationAction action;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(length = 1_000)
    private String reason;

    /** Open reports closed by this decision. */
    @Column(name = "reports_closed", nullable = false)
    private int reportsClosed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationDecision() {
    }

    public ModerationDecision(ReportTargetType targetType, Long targetId, ModerationAction action, String actorId,
                              String actorName, String reason, int reportsClosed) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.actorId = actorId;
        this.actorName = actorName;
        this.reason = reason;
        this.reportsClosed = reportsClosed;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ModerationAction getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getReason() {
        return reason;
    }

    public int getReportsClosed() {
        return reportsClosed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

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

/**
 * A member's report on a post or a comment (S3-US07, carry-over S1-US06). A report never removes anything by
 * itself: the content stays published until an administrator decides (S3-US08).
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_target", columnList = "target_type, target_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** The post itself, or the post of the reported comment (to link to it). */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "reporter_id", nullable = false, length = 100)
    private String reporterId;

    @Column(name = "reporter_name", length = 150)
    private String reporterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 1_000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_by_name", length = 150)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 1_000)
    private String decisionReason;

    protected Report() {
    }

    public Report(ReportTargetType targetType, Long targetId, Long postId, String reporterId, String reporterName,
                  ReportReason reason, String detail) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.reason = reason;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void close(ReportStatus newStatus, String adminId, String adminName, String reason) {
        this.status = newStatus;
        this.decidedBy = adminId;
        this.decidedByName = adminName;
        this.decidedAt = Instant.now();
        this.decisionReason = reason;
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

    public Long getPostId() {
        return postId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getReporterName() {
        return reporterName;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
}

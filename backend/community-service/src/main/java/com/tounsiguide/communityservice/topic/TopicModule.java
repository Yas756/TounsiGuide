package com.tounsiguide.communityservice.topic;

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
 * A topic module (FR-05, S3-US09): a theme proposed by a member and approved by an administrator, used to browse
 * posts by theme. Named TopicModule because {@code java.lang.Module} exists. The slug is unique among modules that
 * are not REJECTED (checked by the service, so a rejected name can be proposed again).
 */
@Entity
@Table(name = "topic_modules", indexes = {
        @Index(name = "idx_topic_modules_slug", columnList = "slug"),
        @Index(name = "idx_topic_modules_status", columnList = "status")
})
public class TopicModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 70)
    private String slug;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    /** Optional emoji shown next to the name. */
    @Column(length = 16)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleStatus status = ModuleStatus.PROPOSED;

    @Column(name = "proposed_by", nullable = false, length = 100)
    private String proposedBy;

    @Column(name = "proposed_by_name", length = 150)
    private String proposedByName;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_by_name", length = 150)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 1_000)
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TopicModule() {
    }

    public TopicModule(String slug, String name, String description, String icon, String proposedBy, String proposedByName) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.proposedBy = proposedBy;
        this.proposedByName = proposedByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void decide(ModuleStatus newStatus, String adminId, String adminName, String reason) {
        this.status = newStatus;
        this.decidedBy = adminId;
        this.decidedByName = adminName;
        this.decidedAt = Instant.now();
        this.decisionReason = reason;
    }

    public boolean isApproved() {
        return status == ModuleStatus.APPROVED;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public String getProposedByName() {
        return proposedByName;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}

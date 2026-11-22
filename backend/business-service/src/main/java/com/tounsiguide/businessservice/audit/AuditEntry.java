package com.tounsiguide.businessservice.audit;

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
 * Append-only history (FR-15): who did what, when and why, for page requests (submitted, approved, rejected),
 * official information changes and wall hide/unhide. Rows are never updated or deleted.
 */
@Entity
@Table(name = "business_audit", indexes = {
        @Index(name = "idx_audit_page", columnList = "page_id"),
        @Index(name = "idx_audit_request", columnList = "request_id")
})
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(name = "page_id")
    private Long pageId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "actor_name", length = 60)
    private String actorName;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEntry() {
    }

    public AuditEntry(AuditAction action, Long pageId, Long requestId, Long postId, String actorId, String actorName,
                      String reason) {
        this.action = action;
        this.pageId = pageId;
        this.requestId = requestId;
        this.postId = postId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public Long getPageId() {
        return pageId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getPostId() {
        return postId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}

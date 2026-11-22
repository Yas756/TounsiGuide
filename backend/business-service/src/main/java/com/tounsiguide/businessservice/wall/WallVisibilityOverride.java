package com.tounsiguide.businessservice.wall;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Whether one post is hidden from one page's wall (S3-B05). This is the only thing a page can change about a
 * post: the post itself, the general feed and the author's profile are never touched. One row per (page, post);
 * every change is also written to the audit table.
 */
@Entity
@Table(name = "wall_visibility_override",
        uniqueConstraints = @UniqueConstraint(name = "uk_wall_override_page_post", columnNames = {"page_id", "post_id"}))
public class WallVisibilityOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false)
    private boolean hidden;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "actor_name", length = 60)
    private String actorName;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected WallVisibilityOverride() {
    }

    public WallVisibilityOverride(Long pageId, Long postId) {
        this.pageId = pageId;
        this.postId = postId;
    }

    public void set(boolean hidden, String reason, String actorId, String actorName) {
        this.hidden = hidden;
        this.reason = reason;
        this.actorId = actorId;
        this.actorName = actorName;
        this.changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPageId() {
        return pageId;
    }

    public Long getPostId() {
        return postId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getReason() {
        return reason;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

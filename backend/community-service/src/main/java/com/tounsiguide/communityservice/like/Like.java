package com.tounsiguide.communityservice.like;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One member's like on a post or a comment (S3-US11). At most one per member and target (unique key); a like
 * changes nothing else: no ranking, no Location, no wall.
 */
@Entity(name = "LikeEntry") // "Like" is a JPQL keyword
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_likes_target_user", columnNames = {"target_type", "target_id", "user_id"}),
        indexes = @Index(name = "idx_likes_target", columnList = "target_type, target_id"))
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private LikeTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Like() {
    }

    public Like(LikeTargetType targetType, Long targetId, String userId) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LikeTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}

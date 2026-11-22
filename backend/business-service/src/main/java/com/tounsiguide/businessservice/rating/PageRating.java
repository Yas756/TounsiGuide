package com.tounsiguide.businessservice.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One member's star rating (1 to 5) of a business page (S3-US12). One per member and page: rating again replaces
 * the stars. A rating never touches the page's official information, its wall or any post.
 */
@Entity
@Table(name = "page_rating",
        uniqueConstraints = @UniqueConstraint(name = "uk_page_rating_user", columnNames = {"page_id", "user_id"}))
public class PageRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    @Column(nullable = false)
    private int stars;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PageRating() {
    }

    public PageRating(Long pageId, String userId, String userName, int stars) {
        this.pageId = pageId;
        this.userId = userId;
        this.userName = userName;
        this.stars = stars;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void change(int stars, String userName) {
        this.stars = stars;
        this.userName = userName;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public int getStars() { return stars; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

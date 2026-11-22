package com.tounsiguide.businessservice.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** A user allowed to act for a page (edit official information, hide posts from its wall). */
@Entity
@Table(name = "page_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_page_member_page_user", columnNames = {"page_id", "user_id"}))
public class PageMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    /** Identity Service user id (UUID from the token). */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "user_name", length = 60)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PageRole role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected PageMember() {
    }

    public PageMember(Long pageId, String userId, String userName, PageRole role) {
        this.pageId = pageId;
        this.userId = userId;
        this.userName = userName;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPageId() {
        return pageId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public PageRole getRole() {
        return role;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}

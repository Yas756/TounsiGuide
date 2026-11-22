package com.tounsiguide.businessservice.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * The official page of a business, always linked to an existing Location of the Location Service (S3-US03).
 * At most one ACTIVE page per Location: {@code activeLocationId} equals {@code locationId} while the page is ACTIVE
 * and is null once it is DELETED, and it carries a unique index (MySQL has no partial unique index).
 */
@Entity
@Table(name = "business_page",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_page_active_location", columnNames = "active_location_id"),
        indexes = @Index(name = "idx_business_page_location", columnList = "location_id"))
public class BusinessPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "active_location_id")
    private Long activeLocationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PageStatus status = PageStatus.ACTIVE;

    /** The approved request this page comes from. */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessPage() {
    }

    public BusinessPage(Long locationId, String name, String address, Long requestId, String verifiedBy, Instant verifiedAt) {
        this.locationId = locationId;
        this.activeLocationId = locationId;
        this.name = name;
        this.address = address;
        this.requestId = requestId;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Soft delete: the page and its history stay, and the Location can get a new page. */
    public void markDeleted() {
        status = PageStatus.DELETED;
        activeLocationId = null;
    }

    public boolean isActive() {
        return status == PageStatus.ACTIVE;
    }

    public void updateOfficialInfo(String name, String description, String openingHours, String phone, String email,
                                   String website, String address) {
        this.name = name;
        this.description = description;
        this.openingHours = openingHours;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public Long getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getWebsite() {
        return website;
    }

    public String getAddress() {
        return address;
    }

    public PageStatus getStatus() {
        return status;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

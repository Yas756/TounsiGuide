package com.tounsiguide.communityservice.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank
    @Size(max = 2_000)
    @Column(name = "content", nullable = false, length = 2_000)
    private String content;

    @NotNull
    @Size(max = 100)
    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    @NotNull
    @Size(max = 150)
    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    /** Id of a Location in the Location &amp; Matching Service (no foreign key: another service owns it). */
    @Column(name = "location_id")
    private Long locationId;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * PUBLISHED or DELETED (soft delete). Stored as text, not as a MySQL enum, so adding RESTRICTED (Sprint 3)
     * needs no ALTER TABLE; the database default fills the rows that existed before the column was added.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'PUBLISHED'")
    private PublicationStatus publicationStatus = PublicationStatus.PUBLISHED;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Id of the user (author or administrator) who deleted the post. */
    @Size(max = 100)
    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    /** Optional topic module (Sprint 3, S3-B08): id of an APPROVED module when it was chosen. */
    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "restricted_at")
    private Instant restrictedAt;

    @Size(max = 100)
    @Column(name = "restricted_by", length = 100)
    private String restrictedBy;

    @Size(max = 1_000)
    @Column(name = "restriction_reason", length = 1_000)
    private String restrictionReason;

    protected Post() {
        // Required by JPA.
    }

    public Post(String content, String authorId, String authorName, Long locationId) {
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.locationId = locationId;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public boolean isDeleted() {
        return publicationStatus == PublicationStatus.DELETED;
    }

    /** Soft delete: the row, its comments and media stay; the post is hidden from everyone but administrators. */
    public void markDeleted(String userId) {
        this.publicationStatus = PublicationStatus.DELETED;
        this.deletedAt = Instant.now();
        this.deletedBy = userId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public boolean isRestricted() {
        return publicationStatus == PublicationStatus.RESTRICTED;
    }

    public boolean isPublished() {
        return publicationStatus == PublicationStatus.PUBLISHED;
    }

    /** Moderation (S3-B07): hidden platform-wide until an administrator restores it. */
    public void markRestricted(String adminId, String reason) {
        this.publicationStatus = PublicationStatus.RESTRICTED;
        this.restrictedAt = Instant.now();
        this.restrictedBy = adminId;
        this.restrictionReason = reason;
    }

    /** Undoes {@link #markRestricted}; the decision history keeps who restricted it and why. */
    public void restore() {
        this.publicationStatus = PublicationStatus.PUBLISHED;
        this.restrictedAt = null;
        this.restrictedBy = null;
        this.restrictionReason = null;
    }

    public Instant getRestrictedAt() {
        return restrictedAt;
    }

    public String getRestrictionReason() {
        return restrictionReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }

        Class<?> otherClass = other instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : other.getClass();
        Class<?> thisClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();

        if (thisClass != otherClass) {
            return false;
        }

        Post post = (Post) other;
        return id != null && Objects.equals(id, post.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", authorId='" + authorId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", locationId=" + locationId +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", publicationStatus=" + publicationStatus +
                '}';
    }
}

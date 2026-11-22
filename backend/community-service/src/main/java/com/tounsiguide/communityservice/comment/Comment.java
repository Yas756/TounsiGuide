package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "post_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_comments_post")
    )
    private Post post;

    @NotBlank
    @Size(max = 1_000)
    @Column(name = "content", nullable = false, length = 1_000)
    private String content;

    @NotNull
    @Size(max = 100)
    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    @NotNull
    @Size(max = 150)
    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** PUBLISHED or RESTRICTED (Sprint 3 moderation). The database default fills the rows created before. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'PUBLISHED'")
    private PublicationStatus publicationStatus = PublicationStatus.PUBLISHED;

    @Column(name = "restricted_at")
    private Instant restrictedAt;

    @Size(max = 100)
    @Column(name = "restricted_by", length = 100)
    private String restrictedBy;

    @Size(max = 1_000)
    @Column(name = "restriction_reason", length = 1_000)
    private String restrictionReason;

    protected Comment() {
        // Required by JPA.
    }

    public Comment(Post post, String content, String authorId, String authorName) {
        this.post = post;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
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

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public boolean isRestricted() {
        return publicationStatus == PublicationStatus.RESTRICTED;
    }

    public void markRestricted(String adminId, String reason) {
        this.publicationStatus = PublicationStatus.RESTRICTED;
        this.restrictedAt = Instant.now();
        this.restrictedBy = adminId;
        this.restrictionReason = reason;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

        Comment comment = (Comment) other;
        return id != null && Objects.equals(id, comment.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", authorId='" + authorId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

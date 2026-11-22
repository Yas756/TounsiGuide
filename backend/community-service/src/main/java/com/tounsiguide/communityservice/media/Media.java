package com.tounsiguide.communityservice.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A photo or video uploaded with POST /api/v1/media. It is first unattached (postId and commentId null), then
 * attached to one post or one comment when its id is sent in {@code mediaIds}. Unattached files older than
 * 24 hours are removed by {@link MediaCleanupJob}.
 */
@Entity
@Table(name = "media", indexes = {
        @Index(name = "idx_media_post", columnList = "post_id"),
        @Index(name = "idx_media_comment", columnList = "comment_id"),
        @Index(name = "idx_media_created", columnList = "created_at")
})
public class Media {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private MediaKind type;

    @NotNull
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Key given by {@link MediaStorage#store}; never the name of the file on the user's computer. */
    @NotNull
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    /** Id of the user who uploaded the file. Only they may attach it. */
    @NotNull
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "position", nullable = false)
    private int position;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Media() {
        // Required by JPA.
    }

    public Media(MediaKind type, String contentType, long sizeBytes, String storageKey, String ownerId) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.ownerId = ownerId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Attaches the file to a post or to a comment (exactly one of the two ids is non-null). */
    public void attach(Long postId, Long commentId, int position) {
        this.postId = postId;
        this.commentId = commentId;
        this.position = position;
    }

    public boolean isAttached() {
        return postId != null || commentId != null;
    }

    public String getId() {
        return id;
    }

    public MediaKind getType() {
        return type;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

        Media media = (Media) other;
        return id != null && Objects.equals(id, media.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Media{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", contentType='" + contentType + '\'' +
                ", sizeBytes=" + sizeBytes +
                ", ownerId='" + ownerId + '\'' +
                ", postId=" + postId +
                ", commentId=" + commentId +
                ", position=" + position +
                ", createdAt=" + createdAt +
                '}';
    }
}

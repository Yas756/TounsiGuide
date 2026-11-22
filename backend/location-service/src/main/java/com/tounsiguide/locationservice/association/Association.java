package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.location.Location;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A claim that a post (Community Service) belongs to a Location, with a confidence score computed from
 * its evidence. One row per (post, Location). The status rules live in {@link AssociationService}.
 */
@Entity
@Table(name = "associations",
        uniqueConstraints = @UniqueConstraint(name = "uk_associations_post_location", columnNames = {"post_id", "location_id"}),
        indexes = {
                @Index(name = "idx_associations_post", columnList = "post_id"),
                @Index(name = "idx_associations_status", columnList = "status")
        })
public class Association {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Post id in the Community Service (no foreign key across services). */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "score", nullable = false)
    private int score;

    /** Set by an admin FORCE: evidence is still recorded but no longer changes the status. */
    @Column(name = "forced", nullable = false)
    private boolean forced;

    /** True while this is the place currently stored in {@code Post.locationId} by the author. */
    @Column(name = "author_claim", nullable = false)
    private boolean authorClaim;

    /** Snapshot of the post author (used for the analyzer's history signal). */
    @Column(name = "post_author_id", length = 100)
    private String postAuthorId;

    /**
     * The post was deleted in the Community Service (it answers 404): the association, its evidence and its history
     * are kept unchanged, hidden from public lists, still shown to administrators, and no longer analysed.
     */
    @Column(name = "post_deleted", nullable = false)
    private boolean postDeleted;

    /** When this service first saw the post as deleted (null while the post exists, or for older rows). */
    @Column(name = "post_deleted_at")
    private Instant postDeletedAt;

    /** Latest analysis as JSON (provider, version, scores, reasoning, mentions). */
    @Column(name = "analysis", columnDefinition = "TEXT")
    private String analysis;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @OneToMany(mappedBy = "association", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AssociationEvidence> evidence = new ArrayList<>();

    @OneToMany(mappedBy = "association", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AssociationDecision> decisions = new ArrayList<>();

    protected Association() {
        // Required by JPA.
    }

    public Association(Long postId, Location location, Origin origin, Status status, String createdBy,
                       String createdByName) {
        this.postId = Objects.requireNonNull(postId);
        this.location = Objects.requireNonNull(location);
        this.origin = Objects.requireNonNull(origin);
        this.status = Objects.requireNonNull(status);
        this.createdBy = createdBy;
        this.createdByName = createdByName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Appends evidence (append-only: nothing is ever removed). */
    public AssociationEvidence addEvidence(EvidenceType type, int weight, String actorId, String actorName,
                                           Long commentId, String note) {
        AssociationEvidence item = new AssociationEvidence(this, type, weight, actorId, actorName, commentId, note);
        evidence.add(item);
        return item;
    }

    /** True when evidence of this type already exists for the same comment (null = the post text). */
    public boolean hasEvidence(EvidenceType type, Long commentId) {
        return evidence.stream().anyMatch(e -> e.getType() == type && Objects.equals(e.getCommentId(), commentId));
    }

    /** True when this actor already gave evidence of this type. */
    public boolean hasEvidenceFrom(EvidenceType type, String actorId) {
        return actorId != null && evidence.stream()
                .anyMatch(e -> e.getType() == type && actorId.equals(e.getActorId()));
    }

    /**
     * Moves to {@code to} and appends the decision to the history. When {@code byPerson} is true the
     * association also remembers who decided and why ({@code decidedBy}, {@code decidedAt}, {@code decisionReason}).
     */
    public AssociationDecision moveTo(Status to, String actorId, String actorRole, String reason, boolean byPerson) {
        AssociationDecision decision = new AssociationDecision(this, status, to, actorId, actorRole, reason);
        decisions.add(decision);
        this.status = to;
        this.updatedAt = Instant.now();
        if (byPerson) {
            this.decidedBy = actorId;
            this.decidedAt = this.updatedAt;
            this.decisionReason = decision.getReason();
        }
        return decision;
    }

    /** First entry of the history, for a new association (from = null). */
    public void recordCreation(String actorId, String actorRole, String reason) {
        decisions.add(new AssociationDecision(this, null, status, actorId, actorRole, reason));
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Location getLocation() {
        return location;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Status getStatus() {
        return status;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public boolean isAuthorClaim() {
        return authorClaim;
    }

    public void setAuthorClaim(boolean authorClaim) {
        this.authorClaim = authorClaim;
    }

    public String getPostAuthorId() {
        return postAuthorId;
    }

    public void setPostAuthorId(String postAuthorId) {
        this.postAuthorId = postAuthorId;
    }

    public boolean isPostDeleted() {
        return postDeleted;
    }

    /** Records that the post is deleted; the first date is kept when the change ping comes again. */
    public void markPostDeleted(Instant seenAt) {
        if (!postDeleted) {
            this.postDeleted = true;
            this.postDeletedAt = seenAt;
        }
    }

    /** The post can be read again. */
    public void markPostPresent() {
        this.postDeleted = false;
        this.postDeletedAt = null;
    }

    public Instant getPostDeletedAt() {
        return postDeletedAt;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public List<AssociationEvidence> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    public List<AssociationDecision> getDecisions() {
        return Collections.unmodifiableList(decisions);
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
        Association association = (Association) other;
        return id != null && Objects.equals(id, association.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Association{id=" + id + ", postId=" + postId + ", status=" + status + ", score=" + score + '}';
    }
}

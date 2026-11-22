package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.association.dto.AdminSetAssociationRequest;
import com.tounsiguide.locationservice.association.dto.AssociationResponse;
import com.tounsiguide.locationservice.association.dto.DecisionResponse;
import com.tounsiguide.locationservice.association.dto.PageResponse;
import com.tounsiguide.locationservice.association.dto.PostAssociationSummary;
import com.tounsiguide.locationservice.association.dto.ReviewRequest;
import com.tounsiguide.locationservice.association.dto.SuggestAssociationRequest;
import com.tounsiguide.locationservice.common.error.BadRequestException;
import com.tounsiguide.locationservice.common.error.ConflictException;
import com.tounsiguide.locationservice.common.security.CurrentUser;
import com.tounsiguide.locationservice.common.security.ForbiddenException;
import com.tounsiguide.locationservice.community.CommunityClient;
import com.tounsiguide.locationservice.community.CommunityClient.PostSnapshot;
import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.location.LocationRepository;
import com.tounsiguide.locationservice.matching.analysis.AnalysisContext.Trigger;
import com.tounsiguide.locationservice.matching.analysis.AnalysisResult;
import com.tounsiguide.locationservice.matching.analysis.FoundMention;
import com.tounsiguide.locationservice.matching.analysis.LocationVerdict;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.tounsiguide.locationservice.association.AssociationDecision.ROLE_ADMIN;
import static com.tounsiguide.locationservice.association.AssociationDecision.ROLE_AUTHOR;
import static com.tounsiguide.locationservice.association.AssociationDecision.ROLE_MEMBER;
import static com.tounsiguide.locationservice.association.AssociationDecision.ROLE_SYSTEM;

/**
 * Associations between posts and Locations: evidence, score and lifecycle (association-api.md, sections 3 and 4).
 *
 * <ul>
 *   <li>At most one association per post is CONFIRMED: confirming one rejects the other in the same transaction.</li>
 *   <li>A {@code forced} association (admin FORCE or direct correction) keeps its status whatever the evidence
 *       says; only an administrator can replace it.</li>
 *   <li>The analyzer adds evidence; it never changes a status by itself. Only people decide, plus one system
 *       rule: a CONFIRMED association whose score falls below the dispute threshold becomes DISPUTED.</li>
 * </ul>
 */
@Service
public class AssociationService {

    static final int MAX_SUMMARY_IDS = 100;
    static final int MAX_PAGE_SIZE = 100;
    static final List<Status> DEFAULT_QUEUE = List.of(Status.DISPUTED, Status.PROPOSED);

    private static final Logger log = LoggerFactory.getLogger(AssociationService.class);

    private final AssociationRepository repository;
    private final LocationRepository locationRepository;
    private final CommunityClient communityClient;
    private final AssociationScorer scorer;
    private final AssociationProperties properties;
    private final AssociationMapper mapper;
    private final ApplicationEventPublisher events;

    public AssociationService(
            AssociationRepository repository,
            LocationRepository locationRepository,
            CommunityClient communityClient,
            AssociationScorer scorer,
            AssociationProperties properties,
            AssociationMapper mapper,
            ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.locationRepository = locationRepository;
        this.communityClient = communityClient;
        this.scorer = scorer;
        this.properties = properties;
        this.mapper = mapper;
        this.events = events;
    }

    // ------------------------------------------------------------------ public reads

    /** Every association of a post, best score first, with evidence. Empty when the post was deleted. */
    @Transactional(readOnly = true)
    public List<AssociationResponse> listForPost(Long postId) {
        return repository.findByPostId(postId).stream()
                .filter(a -> !a.isPostDeleted())
                .sorted(Comparator.comparingInt(Association::getScore).reversed().thenComparing(Association::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toResponse)
                .toList();
    }

    /** For the feed: confirmed place and number of open proposals per post, in the order asked. */
    @Transactional(readOnly = true)
    public Map<String, PostAssociationSummary> summary(List<Long> postIds) {
        List<Long> ids = postIds == null ? List.of() : postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_SUMMARY_IDS) {
            throw new BadRequestException("At most " + MAX_SUMMARY_IDS + " post ids");
        }
        Map<String, PostAssociationSummary> summaries = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return summaries;
        }
        Map<Long, List<Association>> byPost = new LinkedHashMap<>();
        repository.findByPostIdIn(ids).stream()
                .filter(a -> !a.isPostDeleted())
                .forEach(a -> byPost.computeIfAbsent(a.getPostId(), key -> new ArrayList<>()).add(a));
        for (Long id : ids) {
            List<Association> associations = byPost.getOrDefault(id, List.of());
            PostAssociationSummary.Confirmed confirmed = associations.stream()
                    .filter(a -> a.getStatus() == Status.CONFIRMED)
                    .findFirst()
                    .map(a -> new PostAssociationSummary.Confirmed(a.getId(), a.getLocation().getId(), a.getScore()))
                    .orElse(null);
            int open = (int) associations.stream().filter(a -> a.getStatus() == Status.PROPOSED).count();
            summaries.put(String.valueOf(id), new PostAssociationSummary(confirmed, open));
        }
        return summaries;
    }

    /** Post ids confirmed for a Location (Sprint 3 wall). */
    @Transactional(readOnly = true)
    public PageResponse<Long> confirmedPostIds(Long locationId, int page, int size) {
        if (!locationRepository.existsById(locationId)) {
            throw new EntityNotFoundException("Location not found: " + locationId);
        }
        return PageResponse.of(repository.findConfirmedPostIds(locationId, pageRequest(page, size)), id -> id);
    }

    // ------------------------------------------------------------------ admin reads

    /** Review queue: DISPUTED first, then the lowest score (S2-US08). */
    @Transactional(readOnly = true)
    public PageResponse<AssociationResponse> queue(List<Status> statuses, int page, int size, CurrentUser user) {
        requireAdmin(user);
        List<Status> wanted = statuses == null || statuses.isEmpty() ? DEFAULT_QUEUE : statuses;
        return PageResponse.of(repository.findQueue(wanted, pageRequest(page, size)), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AssociationResponse get(Long id, CurrentUser user) {
        requireAdmin(user);
        return mapper.toResponse(findOrThrow(id), true);
    }

    @Transactional(readOnly = true)
    public List<DecisionResponse> history(Long id, CurrentUser user) {
        requireAdmin(user);
        return mapper.toHistory(findOrThrow(id));
    }

    // ------------------------------------------------------------------ member actions

    /**
     * A member suggests a place (S2-US04). A second suggestion of the same place adds evidence to the
     * existing association ({@code created = false}); the same member cannot suggest the same place twice.
     */
    @Transactional
    public SuggestResult suggest(Long postId, SuggestAssociationRequest request, CurrentUser user) {
        PostSnapshot post = requirePost(postId);
        Location location = findLocation(request.locationId());
        List<Association> associations = new ArrayList<>(repository.findByPostId(postId));
        Association association = byLocation(associations, location.getId());
        boolean created = association == null;
        if (!created && association.hasEvidenceFrom(EvidenceType.MEMBER_SUGGESTION, user.id())) {
            throw new ConflictException("You already suggested this place for this post");
        }
        if (created) {
            association = new Association(postId, location, Origin.MEMBER, Status.PROPOSED, user.id(), user.displayName());
            association.setPostAuthorId(post.authorId());
            association.recordCreation(user.id(), ROLE_MEMBER, "Suggérée par un membre");
            associations.add(association);
        }
        addEvidence(association, EvidenceType.MEMBER_SUGGESTION, user.id(), user.displayName(),
                request.commentId(), request.note(), associations);
        refresh(associations, true);
        repository.saveAll(associations);
        return new SuggestResult(mapper.toResponse(association), created);
    }

    public record SuggestResult(AssociationResponse association, boolean created) {
    }

    /** The post author confirms a proposed association (S2-US05). */
    @Transactional
    public AssociationResponse confirm(Long id, CurrentUser user) {
        Association association = findOrThrow(id);
        requirePostAuthor(association, user);
        requireStatus(association, Set.of(Status.PROPOSED), "confirm");
        List<Association> associations = postAssociations(association);
        // Checks first (409 when an admin forced another place), then records the evidence.
        confirmAndRejectOthers(association, associations, user.id(), ROLE_AUTHOR, "Confirmée par l'auteur", false);
        association.addEvidence(EvidenceType.AUTHOR_CONFIRMED, properties.weight(EvidenceType.AUTHOR_CONFIRMED),
                user.id(), user.displayName(), null, null);
        refresh(associations, true);
        repository.saveAll(associations);
        return mapper.toResponse(association);
    }

    /** The post author rejects a proposed association. */
    @Transactional
    public AssociationResponse reject(Long id, CurrentUser user) {
        Association association = findOrThrow(id);
        requirePostAuthor(association, user);
        requireStatus(association, Set.of(Status.PROPOSED), "reject");
        association.moveTo(Status.REJECTED, user.id(), ROLE_AUTHOR, "Rejetée par l'auteur", true);
        repository.save(association);
        return mapper.toResponse(association);
    }

    /** The post author asks a question about a proposal: logged in the history, no status change. */
    @Transactional
    public AssociationResponse clarify(Long id, String note, CurrentUser user) {
        Association association = findOrThrow(id);
        requirePostAuthor(association, user);
        requireStatus(association, Set.of(Status.PROPOSED), "clarify");
        association.moveTo(Status.PROPOSED, user.id(), ROLE_AUTHOR, note, false);
        repository.save(association);
        return mapper.toResponse(association);
    }

    /**
     * A member (not the post author) disputes a confirmed association (S2-US07). It becomes DISPUTED and an
     * analysis starts, unless it is forced: then the dispute is only recorded as evidence. Another member may
     * add their dispute to an association that is already DISPUTED.
     */
    @Transactional
    public AssociationResponse dispute(Long id, String note, CurrentUser user) {
        Association association = findOrThrow(id);
        PostSnapshot post = requirePost(association.getPostId());
        if (user.id().equals(post.authorId())) {
            throw new ForbiddenException("The author cannot dispute the place of their own post; edit the post instead");
        }
        requireStatus(association, Set.of(Status.CONFIRMED, Status.DISPUTED), "dispute");
        if (association.hasEvidenceFrom(EvidenceType.MEMBER_DISPUTE, user.id())) {
            throw new ConflictException("You already disputed this association");
        }
        List<Association> associations = postAssociations(association);
        addEvidence(association, EvidenceType.MEMBER_DISPUTE, user.id(), user.displayName(), null, note, associations);
        boolean started = false;
        if (association.getStatus() == Status.CONFIRMED && !association.isForced()) {
            association.moveTo(Status.DISPUTED, user.id(), ROLE_MEMBER, note, true);
            started = true;
        }
        boolean systemDispute = refresh(associations, false);
        repository.saveAll(associations);
        if (started || systemDispute) {
            events.publishEvent(new AnalysisRequested(association.getPostId(), Trigger.DISPUTED));
        }
        return mapper.toResponse(association);
    }

    // ------------------------------------------------------------------ admin actions

    /** VALIDATE or FORCE a disputed/proposed association, or REMOVE any association. Reason required. */
    @Transactional
    public AssociationResponse review(Long id, ReviewRequest request, CurrentUser user) {
        requireAdmin(user);
        Association association = findOrThrow(id);
        List<Association> associations = postAssociations(association);
        switch (request.decision()) {
            case VALIDATE, FORCE -> {
                requireStatus(association, Set.of(Status.DISPUTED, Status.PROPOSED), request.decision().name());
                confirmAndRejectOthers(association, associations, user.id(), ROLE_ADMIN, request.reason(), true);
                association.setForced(request.decision() == ReviewDecision.FORCE);
            }
            case REMOVE -> {
                requireStatus(association, Set.of(Status.PROPOSED, Status.CONFIRMED, Status.DISPUTED), "REMOVE");
                association.setForced(false);
                association.moveTo(Status.REJECTED, user.id(), ROLE_ADMIN, request.reason(), true);
            }
        }
        boolean systemDispute = refresh(associations, false);
        repository.saveAll(associations);
        if (systemDispute) {
            events.publishEvent(new AnalysisRequested(association.getPostId(), Trigger.DISPUTED));
        }
        return mapper.toResponse(association);
    }

    /** The admin sets the place of a post directly (correction): CONFIRMED and forced. */
    @Transactional
    public AssociationResponse adminSet(Long postId, AdminSetAssociationRequest request, CurrentUser user) {
        requireAdmin(user);
        PostSnapshot post = requirePost(postId);
        Location location = findLocation(request.locationId());
        List<Association> associations = new ArrayList<>(repository.findByPostId(postId));
        Association association = byLocation(associations, location.getId());
        if (association == null) {
            association = new Association(postId, location, Origin.ADMIN, Status.CONFIRMED, user.id(), user.displayName());
            association.setPostAuthorId(post.authorId());
            rejectOtherConfirmed(association, associations, user.id(), ROLE_ADMIN, true);
            association.recordCreation(user.id(), ROLE_ADMIN, request.reason());
            associations.add(association);
        } else if (association.getStatus() == Status.CONFIRMED) {
            association.moveTo(Status.CONFIRMED, user.id(), ROLE_ADMIN, request.reason(), true);
        } else {
            confirmAndRejectOthers(association, associations, user.id(), ROLE_ADMIN, request.reason(), true);
        }
        association.setForced(true);
        refresh(associations, false);
        repository.saveAll(associations);
        return mapper.toResponse(association);
    }

    /** The admin asks for the matcher to run again; it runs in the background. */
    public void rescan(Long postId, CurrentUser user) {
        requireAdmin(user);
        requirePost(postId);
        events.publishEvent(new AnalysisRequested(postId, Trigger.RESCAN));
    }

    // ------------------------------------------------------------------ system (Community change ping, analysis)

    /**
     * Turns {@code Post.locationId} into the AUTHOR claim (section 7). Idempotent: nothing changes when the
     * claim is already recorded. {@code post == null} means the post was deleted (404): every association of the
     * post is only marked {@code postDeleted} (status, score, evidence and history unchanged) and nothing else runs.
     *
     * <ul>
     *   <li>New place: the previous author claim is REJECTED ("Author changed the place") and the new place is
     *       CONFIRMED with AUTHOR_TAG evidence; another CONFIRMED association of the post is REJECTED.</li>
     *   <li>{@code locationId == null}: the previous author claim is REJECTED ("Author claim withdrawn").</li>
     *   <li>A forced association is never changed here. While one is CONFIRMED, the new author claim stays PROPOSED.</li>
     * </ul>
     *
     * @return true when something changed
     */
    @Transactional
    public boolean syncAuthorClaim(long postId, PostSnapshot post) {
        List<Association> associations = new ArrayList<>(repository.findByPostId(postId));
        if (post == null) {
            Instant seenAt = Instant.now();
            associations.forEach(a -> a.markPostDeleted(seenAt));
            repository.saveAll(associations);
            return false;
        }
        String authorId = post.authorId();
        String authorName = post.authorName();
        Long claimed = post.locationId();
        boolean changed = false;
        for (Association association : associations) {
            association.markPostPresent();
            association.setPostAuthorId(authorId);
            if (association.isAuthorClaim() && !association.getLocation().getId().equals(claimed)) {
                association.setAuthorClaim(false);
                changed = true;
                if (association.getStatus() != Status.REJECTED && !association.isForced()) {
                    association.moveTo(Status.REJECTED, authorId, ROLE_AUTHOR,
                            claimed == null ? "L'auteur a retiré le lieu" : "L'auteur a changé de lieu", true);
                }
            }
        }
        if (claimed != null) {
            Association target = byLocation(associations, claimed);
            if (target == null || !target.isAuthorClaim()) {
                Location location = target != null ? target.getLocation() : locationRepository.findById(claimed).orElse(null);
                if (location == null) {
                    log.warn("Post {} claims unknown Location {}: author claim ignored", postId, claimed);
                } else {
                    changed = true;
                    Association current = target;
                    boolean lockedByAdmin = associations.stream().anyMatch(a -> a != current
                            && a.getStatus() == Status.CONFIRMED && a.isForced());
                    if (target == null) {
                        target = new Association(postId, location, Origin.AUTHOR,
                                lockedByAdmin ? Status.PROPOSED : Status.CONFIRMED, authorId, authorName);
                        if (!lockedByAdmin) {
                            rejectOtherConfirmed(target, associations, authorId, ROLE_AUTHOR, true);
                        }
                        target.recordCreation(authorId, ROLE_AUTHOR,
                                lockedByAdmin ? "Lieu choisi par l'auteur ; un administrateur a fixé un autre lieu" : "Lieu choisi par l'auteur");
                        associations.add(target);
                    } else if (!lockedByAdmin && target.getStatus() != Status.CONFIRMED && !target.isForced()) {
                        confirmAndRejectOthers(target, associations, authorId, ROLE_AUTHOR, "Lieu choisi par l'auteur", false);
                    }
                    target.setAuthorClaim(true);
                    target.setPostAuthorId(authorId);
                    target.addEvidence(EvidenceType.AUTHOR_TAG, properties.weight(EvidenceType.AUTHOR_TAG),
                            authorId, authorName, null, null);
                }
            }
        }
        boolean systemDispute = refresh(associations, false);
        repository.saveAll(associations);
        if (systemDispute) {
            events.publishEvent(new AnalysisRequested(postId, Trigger.DISPUTED));
        }
        return changed;
    }

    /**
     * Applies an analyzer result (section 5.1): each mention becomes TEXT_MENTION (or PAGE_MENTION) evidence,
     * and a new PROPOSED association when the place had none; the verdict is stored as {@code analysis}.
     * The same mention (same type, same comment) is never counted twice.
     */
    @Transactional
    public void applyAnalysis(long postId, AnalysisResult result) {
        List<Association> associations = new ArrayList<>(repository.findByPostId(postId));
        if (associations.stream().anyMatch(Association::isPostDeleted)) {
            return;
        }
        Instant analysedAt = Instant.now();
        for (LocationVerdict verdict : result.verdicts()) {
            Association association = byLocation(associations, verdict.locationId());
            for (FoundMention mention : verdict.mentions()) {
                EvidenceType type = mention.type() == FoundMention.Type.PAGE_MENTION
                        ? EvidenceType.PAGE_MENTION : EvidenceType.TEXT_MENTION;
                if (association != null && association.hasEvidence(type, mention.commentId())) {
                    continue;
                }
                if (association == null) {
                    Location location = locationRepository.findById(verdict.locationId()).orElse(null);
                    if (location == null) {
                        break;
                    }
                    association = new Association(postId, location, Origin.MENTION, Status.PROPOSED, null, null);
                    association.recordCreation(null, ROLE_SYSTEM, mention.commentId() == null
                            ? "Lieu cité dans la publication" : "Lieu cité dans le commentaire n° " + mention.commentId());
                    associations.add(association);
                }
                addEvidence(association, type, null, null, mention.commentId(),
                        "Lieu cité : « " + mention.text() + " »", associations);
            }
            if (association != null) {
                association.setAnalysis(mapper.analysisJson(result, verdict, analysedAt));
            }
        }
        refresh(associations, false);
        repository.saveAll(associations);
    }

    // ------------------------------------------------------------------ rules

    /**
     * Appends evidence. Support from members or text competes with the other places of the post: each other
     * association that is not REJECTED receives COMPETING_CLAIM evidence, but <b>at most once per source</b>
     * (2026-11-15). A source is one comment, or one member, or the post text. So a comment whose words match
     * several places ("Medina"), or a member who comments and then suggests from that comment, is one weak competing
     * signal: a single person can never push an author's tag below the dispute threshold (10 / 12 = 83), while two
     * independent sources can (10 / 14 = 71).
     */
    private void addEvidence(Association association, EvidenceType type, String actorId, String actorName,
                             Long commentId, String note, List<Association> postAssociations) {
        association.addEvidence(type, properties.weight(type), actorId, actorName, commentId, note);
        if (!type.competesWithOtherPlaces()) {
            return;
        }
        for (Association other : postAssociations) {
            if (other != association && other.getStatus() != Status.REJECTED
                    && !hasCompetingClaimFrom(other, actorId, commentId)) {
                other.addEvidence(EvidenceType.COMPETING_CLAIM, properties.weight(EvidenceType.COMPETING_CLAIM),
                        actorId, actorName, commentId,
                        "Soutien à « " + association.getLocation().getName() + " » sur la même publication");
            }
        }
    }

    /**
     * True when {@code association} already has a COMPETING_CLAIM from this source: the same comment, or the same
     * member (when the signal has no comment), or the post text (no comment and no member).
     */
    static boolean hasCompetingClaimFrom(Association association, String actorId, Long commentId) {
        return association.getEvidence().stream()
                .filter(e -> e.getType() == EvidenceType.COMPETING_CLAIM)
                .anyMatch(e -> commentId != null
                        ? commentId.equals(e.getCommentId())
                        : actorId != null
                        ? actorId.equals(e.getActorId()) && e.getCommentId() == null
                        : e.getActorId() == null && e.getCommentId() == null);
    }

    /**
     * Recomputes every score. A CONFIRMED, not forced association whose score falls from the threshold or
     * above to below it becomes DISPUTED (system rule). When {@code analyse} is true an analysis is requested.
     *
     * @return true when the system disputed an association
     */
    private boolean refresh(List<Association> associations, boolean analyse) {
        int threshold = properties.disputeThreshold();
        boolean disputed = false;
        for (Association association : associations) {
            int before = association.getScore();
            int after = scorer.score(association);
            association.setScore(after);
            if (association.getStatus() == Status.CONFIRMED && !association.isForced()
                    && before >= threshold && after < threshold) {
                association.moveTo(Status.DISPUTED, null, ROLE_SYSTEM,
                        "Score tombé à " + after + ", sous le seuil de contestation " + threshold, false);
                disputed = true;
            }
        }
        if (disputed && analyse && !associations.isEmpty()) {
            events.publishEvent(new AnalysisRequested(associations.getFirst().getPostId(), Trigger.DISPUTED));
        }
        return disputed;
    }

    /** Confirms {@code association} and rejects the post's other CONFIRMED one (at most one CONFIRMED). */
    private void confirmAndRejectOthers(Association association, List<Association> postAssociations, String actorId,
                                        String role, String reason, boolean byAdmin) {
        rejectOtherConfirmed(association, postAssociations, actorId, role, byAdmin);
        association.moveTo(Status.CONFIRMED, actorId, role, reason, true);
    }

    /**
     * Rejects the other CONFIRMED associations of the post. Only an administrator may replace a forced one
     * (409 otherwise); nothing is changed before that check.
     */
    private static void rejectOtherConfirmed(Association association, List<Association> postAssociations,
                                             String actorId, String role, boolean byAdmin) {
        List<Association> others = postAssociations.stream()
                .filter(other -> other != association && other.getStatus() == Status.CONFIRMED)
                .toList();
        if (!byAdmin && others.stream().anyMatch(Association::isForced)) {
            throw new ConflictException("An administrator fixed the place of this post");
        }
        for (Association other : others) {
            other.setForced(false);
            other.moveTo(Status.REJECTED, actorId, role, "Un autre lieu a été confirmé pour cette publication", true);
        }
    }

    private static void requireStatus(Association association, Set<Status> allowed, String action) {
        if (!allowed.contains(association.getStatus())) {
            throw new ConflictException("Cannot " + action.toLowerCase() + " an association that is "
                    + association.getStatus());
        }
    }

    private static void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Only an administrator can review associations");
        }
    }

    private PostSnapshot requirePostAuthor(Association association, CurrentUser user) {
        PostSnapshot post = requirePost(association.getPostId());
        if (!user.id().equals(post.authorId())) {
            throw new ForbiddenException("Only the author of the post can do this");
        }
        return post;
    }

    /** @throws EntityNotFoundException when the post does not exist (404); 503 when the Community Service is down */
    private PostSnapshot requirePost(Long postId) {
        return communityClient.findPost(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
    }

    private Location findLocation(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException("Location not found: " + locationId));
    }

    private Association findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Association not found: " + id));
    }

    /** All associations of the post, with {@code association} itself as the same instance. */
    private List<Association> postAssociations(Association association) {
        List<Association> associations = new ArrayList<>();
        for (Association other : repository.findByPostId(association.getPostId())) {
            boolean same = other == association
                    || (association.getId() != null && association.getId().equals(other.getId()));
            associations.add(same ? association : other);
        }
        if (associations.stream().noneMatch(a -> a == association)) {
            associations.add(association);
        }
        return associations;
    }

    private static Association byLocation(List<Association> associations, long locationId) {
        return associations.stream()
                .filter(a -> a.getLocation().getId() != null && a.getLocation().getId() == locationId)
                .findFirst()
                .orElse(null);
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
    }
}

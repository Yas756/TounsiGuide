package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.association.dto.AdminSetAssociationRequest;
import com.tounsiguide.locationservice.association.dto.AssociationResponse;
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
import com.tounsiguide.locationservice.location.LocationSource;
import com.tounsiguide.locationservice.matching.analysis.AnalysisContext.Trigger;
import com.tounsiguide.locationservice.matching.analysis.AnalysisResult;
import com.tounsiguide.locationservice.matching.analysis.FoundMention;
import com.tounsiguide.locationservice.matching.analysis.LocationVerdict;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every lifecycle transition of section 4, the score updates, and the 403 / 404 / 409 cases. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssociationServiceTest {

    private static final long POST_ID = 1L;
    private static final CurrentUser AUTHOR = new CurrentUser("author-1", "Sarra", Set.of("MEMBER"));
    private static final CurrentUser MEMBER = new CurrentUser("member-1", "Ali", Set.of("MEMBER"));
    private static final CurrentUser MEMBER_2 = new CurrentUser("member-2", "Amira", Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", Set.of("MEMBER", "ADMIN"));

    @Mock
    private AssociationRepository repository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private CommunityClient communityClient;
    @Mock
    private ApplicationEventPublisher events;

    private final AssociationProperties properties = AssociationProperties.defaults();
    private AssociationService service;

    /** Associations "in the database" for POST_ID. */
    private final List<Association> stored = new ArrayList<>();
    private long nextId = 100;

    private Location cafeRoma;
    private Location cafeRome;
    private Location bardo;

    @BeforeEach
    void setUp() {
        service = new AssociationService(repository, locationRepository, communityClient,
                new AssociationScorer(properties), properties, new AssociationMapper(JsonMapper.builder().build()), events);
        cafeRoma = location(14L, "Café Roma");
        cafeRome = location(15L, "Café Rome");
        bardo = location(5L, "Musée national du Bardo");

        when(communityClient.findPost(POST_ID)).thenReturn(Optional.of(post(14L)));
        when(repository.findByPostId(POST_ID)).thenAnswer(call -> List.copyOf(stored));
        when(repository.findById(anyLong())).thenAnswer(call -> stored.stream()
                .filter(a -> a.getId().equals(call.getArgument(0))).findFirst());
        when(repository.saveAll(any())).thenAnswer(call -> {
            Iterable<Association> saved = call.getArgument(0);
            saved.forEach(this::store);
            return saved;
        });
        when(repository.save(any(Association.class))).thenAnswer(call -> store(call.getArgument(0)));
    }

    // ------------------------------------------------------------------ fixtures

    private Location location(long id, String name) {
        Location location = new Location(name, "Tunis", null, 36.8, 10.18, "cafe", LocationSource.DIRECTORY, null, "seed");
        ReflectionTestUtils.setField(location, "id", id);
        when(locationRepository.findById(id)).thenReturn(Optional.of(location));
        when(locationRepository.existsById(id)).thenReturn(true);
        return location;
    }

    private static PostSnapshot post(Long locationId) {
        return new PostSnapshot(POST_ID, "Great coffee", AUTHOR.id(), AUTHOR.displayName(), locationId,
                Instant.now(), Instant.now());
    }

    private Association store(Association association) {
        if (association.getId() == null) {
            ReflectionTestUtils.setField(association, "id", nextId++);
        }
        if (!stored.contains(association)) {
            stored.add(association);
        }
        return association;
    }

    /** An association already in the database, with evidence from distinct members and its score computed. */
    private Association existing(Location location, Origin origin, Status status, EvidenceType... types) {
        Association association = new Association(POST_ID, location, origin, status, "x", "X");
        int actor = 0;
        for (EvidenceType type : types) {
            association.addEvidence(type, properties.weight(type), "someone-" + actor++, "Someone", null, null);
        }
        association.setScore(new AssociationScorer(properties).score(association));
        if (origin == Origin.AUTHOR) {
            association.setAuthorClaim(true);
        }
        return store(association);
    }

    private static List<EvidenceType> evidenceTypes(Association association) {
        return association.getEvidence().stream().map(AssociationEvidence::getType).toList();
    }

    private static AssociationDecision lastDecision(Association association) {
        return association.getDecisions().getLast();
    }

    // ------------------------------------------------------------------ author tag (none -> CONFIRMED)

    @Test
    void authorTagCreatesConfirmedAuthorAssociationWithScore100() {
        boolean changed = service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(changed).isTrue();
        assertThat(stored).hasSize(1);
        Association association = stored.getFirst();
        assertThat(association.getOrigin()).isEqualTo(Origin.AUTHOR);
        assertThat(association.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(association.getScore()).isEqualTo(100);
        assertThat(association.isAuthorClaim()).isTrue();
        assertThat(evidenceTypes(association)).containsExactly(EvidenceType.AUTHOR_TAG);
        assertThat(association.getDecisions()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getFromStatus()).isNull();
                    assertThat(d.getToStatus()).isEqualTo(Status.CONFIRMED);
                    assertThat(d.getActorRole()).isEqualTo("AUTHOR");
                });
    }

    @Test
    void authorTagSyncIsIdempotent() {
        service.syncAuthorClaim(POST_ID, post(14L));
        boolean changedAgain = service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(changedAgain).isFalse();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getEvidence()).hasSize(1);
        assertThat(stored.getFirst().getDecisions()).hasSize(1);
    }

    @Test
    void authorChangingThePlaceRejectsTheOldClaimAndConfirmsTheNewOne() {
        Association old = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(old.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(old.isAuthorClaim()).isFalse();
        assertThat(old.getDecisionReason()).isEqualTo("L'auteur a changé de lieu");
        Association current = stored.stream().filter(a -> a.getLocation() == cafeRoma).findFirst().orElseThrow();
        assertThat(current.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(stored.stream().filter(a -> a.getStatus() == Status.CONFIRMED)).hasSize(1);
    }

    @Test
    void authorRemovingThePlaceWithdrawsTheClaim() {
        Association old = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.syncAuthorClaim(POST_ID, post(null));

        assertThat(old.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(old.getDecisionReason()).isEqualTo("L'auteur a retiré le lieu");
    }

    @Test
    void authorTaggingASuggestedPlaceConfirmsTheExistingAssociation() {
        Association suggested = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(stored).hasSize(1);
        assertThat(suggested.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(suggested.isAuthorClaim()).isTrue();
        assertThat(evidenceTypes(suggested)).containsExactly(EvidenceType.MEMBER_SUGGESTION, EvidenceType.AUTHOR_TAG);
        assertThat(suggested.getScore()).isEqualTo(87);   // 13 / (13 + 2)
    }

    @Test
    void newAuthorClaimStaysProposedWhileAnAdminForcedAnotherPlace() {
        Association forced = existing(bardo, Origin.ADMIN, Status.CONFIRMED);
        forced.setForced(true);

        service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(forced.getStatus()).isEqualTo(Status.CONFIRMED);
        Association authorClaim = stored.stream().filter(a -> a.getLocation() == cafeRoma).findFirst().orElseThrow();
        assertThat(authorClaim.getStatus()).isEqualTo(Status.PROPOSED);
    }

    @Test
    void deletedPostKeepsAssociationsButHidesThemFromPublicLists() {
        existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.syncAuthorClaim(POST_ID, null);

        assertThat(stored).singleElement().satisfies(a -> assertThat(a.isPostDeleted()).isTrue());
        assertThat(service.listForPost(POST_ID)).isEmpty();
    }

    @Test
    void aDeletedPostOnlyFlagsItsAssociationsAndKeepsStatusesScoresEvidenceAndHistory() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        Association disputed = existing(bardo, Origin.MEMBER, Status.DISPUTED,
                EvidenceType.MEMBER_SUGGESTION, EvidenceType.MEMBER_DISPUTE);
        int disputedScore = disputed.getScore();
        int decisions = disputed.getDecisions().size();

        boolean changed = service.syncAuthorClaim(POST_ID, null);

        assertThat(changed).isFalse();
        assertThat(confirmed.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(confirmed.getScore()).isEqualTo(100);
        assertThat(confirmed.isAuthorClaim()).isTrue();
        assertThat(disputed.getStatus()).isEqualTo(Status.DISPUTED);
        assertThat(disputed.getScore()).isEqualTo(disputedScore);
        assertThat(disputed.getEvidence()).hasSize(2);
        assertThat(disputed.getDecisions()).hasSize(decisions);
        assertThat(stored).allSatisfy(a -> {
            assertThat(a.isPostDeleted()).isTrue();
            assertThat(a.getPostDeletedAt()).isNotNull();
        });
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aSecondChangePingKeepsTheFirstDeletionDate() {
        Association association = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        service.syncAuthorClaim(POST_ID, null);
        Instant first = association.getPostDeletedAt();

        service.syncAuthorClaim(POST_ID, null);

        assertThat(association.getPostDeletedAt()).isEqualTo(first);
    }

    @Test
    void theAdminSeesThatThePostWasDeleted() {
        Association association = existing(cafeRoma, Origin.AUTHOR, Status.DISPUTED, EvidenceType.AUTHOR_TAG);
        service.syncAuthorClaim(POST_ID, null);
        when(repository.findQueue(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(association)));

        AssociationResponse inQueue = service.queue(null, 0, 20, ADMIN).content().getFirst();
        AssociationResponse single = service.get(association.getId(), ADMIN);

        assertThat(inQueue.postDeleted()).isTrue();
        assertThat(inQueue.postDeletedAt()).isNotNull();
        assertThat(single.postDeleted()).isTrue();
    }

    @Test
    void anExistingPostIsNotFlagged() {
        existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.syncAuthorClaim(POST_ID, post(14L));

        assertThat(stored).singleElement().satisfies(a -> {
            assertThat(a.isPostDeleted()).isFalse();
            assertThat(a.getPostDeletedAt()).isNull();
        });
        assertThat(service.listForPost(POST_ID).getFirst().postDeleted()).isFalse();
    }

    @Test
    void noAnalysisIsAppliedToADeletedPost() {
        Association association = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        service.syncAuthorClaim(POST_ID, null);
        AnalysisResult result = new AnalysisResult("rule-based", "rules-1", List.of(
                new LocationVerdict(5L, 50, 50, List.of(
                        new FoundMention("Bardo", 88L, FoundMention.Type.TEXT_MENTION)), "named")), "reasoning");

        service.applyAnalysis(POST_ID, result);

        assertThat(stored).containsExactly(association);
        assertThat(association.getAnalysis()).isNull();
        assertThat(association.getEvidence()).hasSize(1);
    }

    // ------------------------------------------------------------------ suggestion (none -> PROPOSED)

    @Test
    void memberSuggestionCreatesProposedAssociation() {
        when(communityClient.findPost(POST_ID)).thenReturn(Optional.of(post(null)));

        AssociationService.SuggestResult result = service.suggest(POST_ID,
                new SuggestAssociationRequest(14L, "The sign is visible", 88L), MEMBER);

        assertThat(result.created()).isTrue();
        AssociationResponse response = result.association();
        assertThat(response.origin()).isEqualTo(Origin.MEMBER);
        assertThat(response.status()).isEqualTo(Status.PROPOSED);
        assertThat(response.score()).isEqualTo(60);   // 3 / (3 + 2)
        assertThat(response.evidence()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(EvidenceType.MEMBER_SUGGESTION);
            assertThat(e.weight()).isEqualTo(3);
            assertThat(e.actorName()).isEqualTo("Ali");
            assertThat(e.commentId()).isEqualTo(88L);
        });
    }

    @Test
    void secondSuggestionOfTheSamePlaceAddsEvidence() {
        existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        AssociationService.SuggestResult result = service.suggest(POST_ID,
                new SuggestAssociationRequest(14L, null, null), MEMBER_2);

        assertThat(result.created()).isFalse();
        assertThat(result.association().score()).isEqualTo(75);   // 6 / (6 + 2)
        assertThat(stored).hasSize(1);
    }

    @Test
    void sameMemberCannotSuggestTheSamePlaceTwice() {
        service.suggest(POST_ID, new SuggestAssociationRequest(14L, null, null), MEMBER);

        assertThatThrownBy(() -> service.suggest(POST_ID, new SuggestAssociationRequest(14L, null, null), MEMBER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void suggestionForAnotherPlaceIsACompetingClaim() {
        Association authorClaim = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.suggest(POST_ID, new SuggestAssociationRequest(14L, null, null), MEMBER);

        assertThat(evidenceTypes(authorClaim)).containsExactly(EvidenceType.AUTHOR_TAG, EvidenceType.COMPETING_CLAIM);
        assertThat(authorClaim.getScore()).isEqualTo(83);   // 10 / 12, still above 75
        assertThat(authorClaim.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void systemDisputesWhenTheScoreFallsBelowTheThreshold() {
        Association authorClaim = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        service.suggest(POST_ID, new SuggestAssociationRequest(14L, null, null), MEMBER);   // 83
        service.suggest(POST_ID, new SuggestAssociationRequest(5L, null, null), MEMBER_2);  // 71 < 75

        assertThat(authorClaim.getScore()).isEqualTo(71);
        assertThat(authorClaim.getStatus()).isEqualTo(Status.DISPUTED);
        assertThat(lastDecision(authorClaim).getActorRole()).isEqualTo("SYSTEM");
        verify(events).publishEvent(new AnalysisRequested(POST_ID, Trigger.DISPUTED));
    }

    @Test
    void suggestionForUnknownPostOrLocationIs404() {
        when(communityClient.findPost(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest(99L, new SuggestAssociationRequest(14L, null, null), MEMBER))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Post");
        assertThatThrownBy(() -> service.suggest(POST_ID, new SuggestAssociationRequest(404L, null, null), MEMBER))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Location");
    }

    // ------------------------------------------------------------------ author confirm / reject / clarify

    @Test
    void authorConfirmsProposalAndTheOtherConfirmedOneIsRejected() {
        Association authorClaim = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        AssociationResponse response = service.confirm(proposal.getId(), AUTHOR);

        assertThat(response.status()).isEqualTo(Status.CONFIRMED);
        assertThat(evidenceTypes(proposal)).contains(EvidenceType.AUTHOR_CONFIRMED);
        assertThat(response.score()).isEqualTo(87);
        assertThat(response.decidedBy()).isEqualTo(AUTHOR.id());
        assertThat(authorClaim.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(stored.stream().filter(a -> a.getStatus() == Status.CONFIRMED)).hasSize(1);
    }

    @Test
    void onlyThePostAuthorCanConfirmRejectOrClarify() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        assertThatThrownBy(() -> service.confirm(proposal.getId(), MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reject(proposal.getId(), MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.clarify(proposal.getId(), "?", MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThat(proposal.getStatus()).isEqualTo(Status.PROPOSED);
    }

    @Test
    void confirmingARejectedAssociationIs409() {
        Association rejected = existing(cafeRoma, Origin.MEMBER, Status.REJECTED, EvidenceType.MEMBER_SUGGESTION);

        assertThatThrownBy(() -> service.confirm(rejected.getId(), AUTHOR)).isInstanceOf(ConflictException.class);
    }

    @Test
    void authorCannotConfirmOverAPlaceForcedByAnAdmin() {
        Association forced = existing(bardo, Origin.ADMIN, Status.CONFIRMED);
        forced.setForced(true);
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        assertThatThrownBy(() -> service.confirm(proposal.getId(), AUTHOR)).isInstanceOf(ConflictException.class);
        assertThat(forced.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(proposal.getStatus()).isEqualTo(Status.PROPOSED);
        assertThat(proposal.getEvidence()).hasSize(1);
    }

    @Test
    void authorRejectsProposal() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        AssociationResponse response = service.reject(proposal.getId(), AUTHOR);

        assertThat(response.status()).isEqualTo(Status.REJECTED);
        assertThat(lastDecision(proposal).getFromStatus()).isEqualTo(Status.PROPOSED);
    }

    @Test
    void rejectingAConfirmedAssociationIs409() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        assertThatThrownBy(() -> service.reject(confirmed.getId(), AUTHOR)).isInstanceOf(ConflictException.class);
    }

    @Test
    void clarifyLogsTheQuestionWithoutChangingTheStatus() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        AssociationResponse response = service.clarify(proposal.getId(), "Which branch?", AUTHOR);

        assertThat(response.status()).isEqualTo(Status.PROPOSED);
        AssociationDecision decision = lastDecision(proposal);
        assertThat(decision.getFromStatus()).isEqualTo(Status.PROPOSED);
        assertThat(decision.getToStatus()).isEqualTo(Status.PROPOSED);
        assertThat(decision.getReason()).isEqualTo("Which branch?");
        assertThat(response.decidedBy()).isNull();
    }

    @Test
    void clarifyOnAConfirmedAssociationIs409() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        assertThatThrownBy(() -> service.clarify(confirmed.getId(), "?", AUTHOR)).isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------------ dispute (CONFIRMED -> DISPUTED)

    @Test
    void memberDisputeMovesToDisputedAndStartsAnAnalysis() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        AssociationResponse response = service.dispute(confirmed.getId(), "It was Café Rome", MEMBER);

        assertThat(response.status()).isEqualTo(Status.DISPUTED);
        assertThat(response.score()).isEqualTo(77);
        assertThat(evidenceTypes(confirmed)).containsExactly(EvidenceType.AUTHOR_TAG, EvidenceType.MEMBER_DISPUTE);
        assertThat(lastDecision(confirmed).getActorRole()).isEqualTo("MEMBER");
        verify(events).publishEvent(new AnalysisRequested(POST_ID, Trigger.DISPUTED));
    }

    @Test
    void authorCannotDisputeTheirOwnPost() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        assertThatThrownBy(() -> service.dispute(confirmed.getId(), "changed my mind", AUTHOR))
                .isInstanceOf(ForbiddenException.class);
        assertThat(confirmed.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void disputingAProposedOrRejectedAssociationIs409() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);
        Association rejected = existing(cafeRome, Origin.MEMBER, Status.REJECTED, EvidenceType.MEMBER_SUGGESTION);

        assertThatThrownBy(() -> service.dispute(proposal.getId(), "no", MEMBER)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.dispute(rejected.getId(), "no", MEMBER)).isInstanceOf(ConflictException.class);
    }

    @Test
    void sameMemberCannotDisputeTwiceButAnotherMemberCanAddTheirs() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        service.dispute(confirmed.getId(), "wrong place", MEMBER);

        assertThatThrownBy(() -> service.dispute(confirmed.getId(), "again", MEMBER)).isInstanceOf(ConflictException.class);

        AssociationResponse response = service.dispute(confirmed.getId(), "agree, wrong", MEMBER_2);
        assertThat(response.status()).isEqualTo(Status.DISPUTED);
        assertThat(response.score()).isEqualTo(63);   // 10 / 16
    }

    @Test
    void disputeOnAForcedAssociationIsOnlyRecorded() {
        Association forced = existing(cafeRoma, Origin.ADMIN, Status.CONFIRMED);
        forced.setForced(true);

        AssociationResponse response = service.dispute(forced.getId(), "not here", MEMBER);

        assertThat(response.status()).isEqualTo(Status.CONFIRMED);
        assertThat(evidenceTypes(forced)).containsExactly(EvidenceType.MEMBER_DISPUTE);
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------ admin review

    @Test
    void onlyAnAdminCanReview() {
        Association disputed = existing(cafeRoma, Origin.AUTHOR, Status.DISPUTED, EvidenceType.AUTHOR_TAG);

        assertThatThrownBy(() -> service.review(disputed.getId(), new ReviewRequest(ReviewDecision.VALIDATE, "ok"), MEMBER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.queue(null, 0, 20, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.get(disputed.getId(), AUTHOR)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.history(disputed.getId(), AUTHOR)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.adminSet(POST_ID, new AdminSetAssociationRequest(14L, "fix"), MEMBER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.rescan(POST_ID, MEMBER)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminValidatesDisputedAssociation() {
        Association disputed = existing(cafeRoma, Origin.AUTHOR, Status.DISPUTED,
                EvidenceType.AUTHOR_TAG, EvidenceType.MEMBER_DISPUTE);

        AssociationResponse response = service.review(disputed.getId(),
                new ReviewRequest(ReviewDecision.VALIDATE, "Photo shows the sign"), ADMIN);

        assertThat(response.status()).isEqualTo(Status.CONFIRMED);
        assertThat(response.forced()).isFalse();
        assertThat(response.decidedBy()).isEqualTo(ADMIN.id());
        assertThat(response.decisionReason()).isEqualTo("Photo shows the sign");
        assertThat(lastDecision(disputed).getActorRole()).isEqualTo("ADMIN");
    }

    @Test
    void adminValidatesProposalAndTheConfirmedOneIsRejected() {
        Association authorClaim = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        service.review(proposal.getId(), new ReviewRequest(ReviewDecision.VALIDATE, "right place"), ADMIN);

        assertThat(proposal.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(authorClaim.getStatus()).isEqualTo(Status.REJECTED);
    }

    @Test
    void validatingAConfirmedOrRejectedAssociationIs409() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        Association rejected = existing(cafeRome, Origin.MEMBER, Status.REJECTED, EvidenceType.MEMBER_SUGGESTION);

        assertThatThrownBy(() -> service.review(confirmed.getId(), new ReviewRequest(ReviewDecision.VALIDATE, "x"), ADMIN))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.review(rejected.getId(), new ReviewRequest(ReviewDecision.FORCE, "x"), ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminForceKeepsTheAssociationConfirmedWhateverTheEvidence() {
        Association disputed = existing(cafeRoma, Origin.MENTION, Status.DISPUTED,
                EvidenceType.TEXT_MENTION, EvidenceType.MEMBER_DISPUTE);

        service.review(disputed.getId(), new ReviewRequest(ReviewDecision.FORCE, "Checked on site"), ADMIN);
        service.dispute(disputed.getId(), "still wrong", MEMBER_2);

        assertThat(disputed.isForced()).isTrue();
        assertThat(disputed.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void adminForceReplacesAnotherForcedAssociation() {
        Association forced = existing(bardo, Origin.ADMIN, Status.CONFIRMED);
        forced.setForced(true);
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);

        service.review(proposal.getId(), new ReviewRequest(ReviewDecision.FORCE, "correction"), ADMIN);

        assertThat(proposal.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(proposal.isForced()).isTrue();
        assertThat(forced.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(forced.isForced()).isFalse();
    }

    @Test
    void adminRemovesFromAnyStatusExceptRejected() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);
        Association disputed = existing(cafeRome, Origin.AUTHOR, Status.DISPUTED, EvidenceType.AUTHOR_TAG);
        Association confirmed = existing(bardo, Origin.ADMIN, Status.CONFIRMED);
        confirmed.setForced(true);

        for (Association association : List.of(proposal, disputed, confirmed)) {
            AssociationResponse response = service.review(association.getId(),
                    new ReviewRequest(ReviewDecision.REMOVE, "spam"), ADMIN);
            assertThat(response.status()).isEqualTo(Status.REJECTED);
            assertThat(response.forced()).isFalse();
        }
        assertThatThrownBy(() -> service.review(proposal.getId(), new ReviewRequest(ReviewDecision.REMOVE, "again"), ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminSetsThePlaceDirectlyAsForcedConfirmed() {
        Association authorClaim = existing(cafeRome, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);

        AssociationResponse response = service.adminSet(POST_ID, new AdminSetAssociationRequest(14L, "Correction"), ADMIN);

        assertThat(response.origin()).isEqualTo(Origin.ADMIN);
        assertThat(response.status()).isEqualTo(Status.CONFIRMED);
        assertThat(response.forced()).isTrue();
        assertThat(authorClaim.getStatus()).isEqualTo(Status.REJECTED);
    }

    @Test
    void rescanRequestsAnAnalysis() {
        service.rescan(POST_ID, ADMIN);

        verify(events).publishEvent(new AnalysisRequested(POST_ID, Trigger.RESCAN));
    }

    @Test
    void historyIsReadFromTheDecisions() {
        Association proposal = existing(cafeRoma, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);
        service.reject(proposal.getId(), AUTHOR);

        assertThat(service.history(proposal.getId(), ADMIN)).singleElement()
                .satisfies(d -> assertThat(d.toStatus()).isEqualTo(Status.REJECTED));
        assertThat(service.get(proposal.getId(), ADMIN).history()).hasSize(1);
    }

    @Test
    void unknownAssociationIs404() {
        assertThatThrownBy(() -> service.confirm(999L, AUTHOR)).isInstanceOf(EntityNotFoundException.class);
    }

    // ------------------------------------------------------------------ analysis results

    @Test
    void mentionsBecomeEvidenceAndNewProposalsOnlyOnce() {
        AnalysisResult result = new AnalysisResult("rule-based", "rules-1", List.of(
                new LocationVerdict(14L, 67, 33, List.of(
                        new FoundMention("Café Roma", 88L, FoundMention.Type.TEXT_MENTION),
                        new FoundMention("Café Roma", 89L, FoundMention.Type.TEXT_MENTION)), "named twice")),
                "Two comments name 'Café Roma'.");

        service.applyAnalysis(POST_ID, result);
        service.applyAnalysis(POST_ID, result);

        assertThat(stored).singleElement().satisfies(a -> {
            assertThat(a.getOrigin()).isEqualTo(Origin.MENTION);
            assertThat(a.getStatus()).isEqualTo(Status.PROPOSED);
            assertThat(a.getEvidence()).hasSize(2);
            assertThat(a.getScore()).isEqualTo(67);
            assertThat(a.getAnalysis()).contains("\"provider\":\"rule-based\"").contains("\"providerVersion\":\"rules-1\"");
        });
    }

    @Test
    void analysisNeverChangesAStatusByItself() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        AnalysisResult result = new AnalysisResult("rule-based", "rules-1", List.of(
                new LocationVerdict(14L, 10, 90, List.of(), "AI thinks otherwise")), "reasoning");

        service.applyAnalysis(POST_ID, result);

        assertThat(confirmed.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(confirmed.getScore()).isEqualTo(100);
        assertThat(service.listForPost(POST_ID).getFirst().analysis()).containsEntry("supportScore", 10);
    }

    // ------------------------------------------------------------------ softer dispute rule (2026-11-15)

    private AnalysisResult mentionsInComment(long commentId, long... locationIds) {
        List<LocationVerdict> verdicts = new ArrayList<>();
        for (long id : locationIds) {
            verdicts.add(new LocationVerdict(id, 50, 50,
                    List.of(new FoundMention("Medina", commentId, FoundMention.Type.TEXT_MENTION)), "named"));
        }
        return new AnalysisResult("rule-based", "rules-1", verdicts, "reasoning");
    }

    private Association authorTag() {
        Association tag = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        tag.setScore(100);
        return tag;
    }

    @Test
    void smokeScenarioOneAmbiguousCommentThenASuggestionFromItNoLongerDisputesTheAuthorTag() {
        // Before 2026-11-15: 71 after the comment (two places named), 63 after the suggestion -> DISPUTED.
        Association tag = authorTag();

        service.applyAnalysis(POST_ID, mentionsInComment(88L, 15L, 5L));
        assertThat(tag.getScore()).isEqualTo(83);
        assertThat(tag.getStatus()).isEqualTo(Status.CONFIRMED);

        service.suggest(POST_ID, new SuggestAssociationRequest(15L, "from my comment", 88L), MEMBER);

        assertThat(evidenceTypes(tag)).containsExactly(EvidenceType.AUTHOR_TAG, EvidenceType.COMPETING_CLAIM);
        assertThat(tag.getScore()).isEqualTo(83);
        assertThat(tag.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void oneMemberSuggestingTwoPlacesIsStillOneCompetingSignal() {
        Association tag = authorTag();

        service.suggest(POST_ID, new SuggestAssociationRequest(15L, null, null), MEMBER);
        service.suggest(POST_ID, new SuggestAssociationRequest(5L, null, null), MEMBER);

        assertThat(tag.getScore()).isEqualTo(83);
        assertThat(tag.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void twoIndependentMembersDisagreeingStillDisputeTheAuthorTag() {
        Association tag = authorTag();

        service.suggest(POST_ID, new SuggestAssociationRequest(15L, null, null), MEMBER);
        service.suggest(POST_ID, new SuggestAssociationRequest(5L, null, null), MEMBER_2);

        assertThat(tag.getScore()).isEqualTo(71);   // 10 / (10 + 2 + 2)
        assertThat(tag.getStatus()).isEqualTo(Status.DISPUTED);
    }

    @Test
    void twoDifferentCommentsNamingAnotherPlaceStillDisputeTheAuthorTag() {
        Association tag = authorTag();

        service.applyAnalysis(POST_ID, mentionsInComment(88L, 15L));
        service.applyAnalysis(POST_ID, mentionsInComment(89L, 15L));

        assertThat(tag.getScore()).isEqualTo(71);
        assertThat(tag.getStatus()).isEqualTo(Status.DISPUTED);
    }

    @Test
    void competingClaimSourcesAreTheCommentTheMemberOrThePostText() {
        Association other = existing(bardo, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);
        other.addEvidence(EvidenceType.COMPETING_CLAIM, 2, null, null, 88L, "from comment 88");
        other.addEvidence(EvidenceType.COMPETING_CLAIM, 2, "member-1", "Ali", null, "from Ali");

        assertThat(AssociationService.hasCompetingClaimFrom(other, "member-9", 88L)).isTrue();
        assertThat(AssociationService.hasCompetingClaimFrom(other, null, 89L)).isFalse();
        assertThat(AssociationService.hasCompetingClaimFrom(other, "member-1", null)).isTrue();
        assertThat(AssociationService.hasCompetingClaimFrom(other, "member-2", null)).isFalse();
        assertThat(AssociationService.hasCompetingClaimFrom(other, null, null)).isFalse();
    }

    // ------------------------------------------------------------------ feed summary

    @Test
    void summaryGivesConfirmedPlaceAndOpenProposalsPerPost() {
        Association confirmed = existing(cafeRoma, Origin.AUTHOR, Status.CONFIRMED, EvidenceType.AUTHOR_TAG);
        existing(cafeRome, Origin.MEMBER, Status.PROPOSED, EvidenceType.MEMBER_SUGGESTION);
        when(repository.findByPostIdIn(List.of(POST_ID, 2L))).thenReturn(List.copyOf(stored));

        var summary = service.summary(List.of(POST_ID, 2L));

        assertThat(summary.get("1").confirmed().associationId()).isEqualTo(confirmed.getId());
        assertThat(summary.get("1").confirmed().locationId()).isEqualTo(14L);
        assertThat(summary.get("1").openProposals()).isEqualTo(1);
        assertThat(summary.get("2").confirmed()).isNull();
        assertThat(summary.get("2").openProposals()).isZero();
    }

    @Test
    void summaryAcceptsAtMostHundredIds() {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service.summary(ids)).isInstanceOf(BadRequestException.class);
    }
}

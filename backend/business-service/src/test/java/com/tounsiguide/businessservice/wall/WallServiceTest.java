package com.tounsiguide.businessservice.wall;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditEntryRepository;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.client.CommunityClient;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.BusinessPageRepository;
import com.tounsiguide.businessservice.page.PageAccess;
import com.tounsiguide.businessservice.page.PageMember;
import com.tounsiguide.businessservice.page.PageMemberRepository;
import com.tounsiguide.businessservice.page.PageRole;
import com.tounsiguide.businessservice.page.PageStatus;
import com.tounsiguide.businessservice.wall.dto.WallPostResponse;
import com.tounsiguide.businessservice.wall.dto.WallVisibilityResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.tounsiguide.businessservice.TestData.ADMIN;
import static com.tounsiguide.businessservice.TestData.MEMBER;
import static com.tounsiguide.businessservice.TestData.OTHER;
import static com.tounsiguide.businessservice.TestData.page;
import static com.tounsiguide.businessservice.TestData.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WallServiceTest {

    /** The page was verified on 2026-11-15; posts 3 and 5 are older than the page. */
    private static final Instant OLD = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NEW = Instant.parse("2026-11-20T10:00:00Z");

    @Mock
    private BusinessPageRepository pages;
    @Mock
    private PageMemberRepository members;
    @Mock
    private LocationClient locationClient;
    @Mock
    private CommunityClient communityClient;
    @Mock
    private WallVisibilityOverrideRepository overrides;
    @Mock
    private AuditService audit;
    @Mock
    private AuditEntryRepository auditEntries;

    private WallService service;

    @BeforeEach
    void setUp() {
        service = new WallService(new PageAccess(pages, members), locationClient, communityClient, overrides, audit,
                auditEntries);
        lenient().when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));
        lenient().when(members.findByPageIdAndUserId(30L, "user-1"))
                .thenReturn(Optional.of(new PageMember(30L, "user-1", "Sarra", PageRole.OWNER)));
    }

    private static WallVisibilityOverride hidden(long postId) {
        WallVisibilityOverride override = new WallVisibilityOverride(30L, postId);
        override.set(true, "Hors sujet", "user-1", "Sarra");
        return override;
    }

    private void wallWith(List<Long> confirmedIds) {
        when(locationClient.confirmedPostIds(14L)).thenReturn(confirmedIds);
        lenient().when(communityClient.findPublishedPosts(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> post(id, id < 6 ? OLD : NEW)).toList();
        });
    }

    // ----------------------------------------------------------------- composition (S3-US05)

    @Test
    void theWallListsConfirmedPostsNewestFirstIncludingPostsOlderThanThePage() {
        wallWith(List.of(5L, 9L, 3L));

        PageResponse<WallPostResponse> wall = service.wall(30L, null, false, 0, 20);

        assertThat(wall.content()).extracting(WallPostResponse::id).containsExactly(9L, 5L, 3L);
        assertThat(wall.content().get(2).createdAt()).isBefore(page(30, 14).getCreatedAt());
        assertThat(wall.content()).noneMatch(WallPostResponse::hidden);
        assertThat(wall.totalElements()).isEqualTo(3);
        assertThat(wall.totalPages()).isEqualTo(1);
    }

    @Test
    void aHiddenPostIsLeftOutForVisitorsAndOtherUsers() {
        wallWith(List.of(5L, 9L, 3L));
        when(overrides.findAllByPageIdAndHiddenTrue(30L)).thenReturn(List.of(hidden(5)));

        PageResponse<WallPostResponse> forVisitor = service.wall(30L, null, true, 0, 20);
        PageResponse<WallPostResponse> forOther = service.wall(30L, OTHER, true, 0, 20);

        assertThat(forVisitor.content()).extracting(WallPostResponse::id).containsExactly(9L, 3L);
        assertThat(forVisitor.totalElements()).isEqualTo(2);
        assertThat(forOther.content()).extracting(WallPostResponse::id).containsExactly(9L, 3L);
    }

    @Test
    void aMemberSeesHiddenPostsFlaggedOnlyWhenAsking() {
        wallWith(List.of(5L, 9L, 3L));
        when(overrides.findAllByPageIdAndHiddenTrue(30L)).thenReturn(List.of(hidden(5)));

        PageResponse<WallPostResponse> normal = service.wall(30L, MEMBER, false, 0, 20);
        PageResponse<WallPostResponse> withHidden = service.wall(30L, MEMBER, true, 0, 20);

        assertThat(normal.content()).extracting(WallPostResponse::id).containsExactly(9L, 3L);
        assertThat(withHidden.content()).extracting(WallPostResponse::id).containsExactly(9L, 5L, 3L);
        WallPostResponse hiddenPost = withHidden.content().get(1);
        assertThat(hiddenPost.hidden()).isTrue();
        assertThat(hiddenPost.hiddenReason()).isEqualTo("Hors sujet");
        assertThat(hiddenPost.hiddenAt()).isNotNull();
    }

    @Test
    void theWallIsPagedAndOnlyThePageOfPostsIsReadFromCommunity() {
        wallWith(List.of(1L, 2L, 3L, 4L, 5L));

        PageResponse<WallPostResponse> second = service.wall(30L, null, false, 1, 2);

        assertThat(second.content()).extracting(WallPostResponse::id).containsExactly(3L, 2L);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);
        verify(communityClient).findPublishedPosts(List.of(3L, 2L));
    }

    @Test
    void aPostDeletedInCommunityIsSkipped() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(9L, 5L));
        when(communityClient.findPublishedPosts(anyCollection())).thenReturn(List.of(post(9, NEW)));

        PageResponse<WallPostResponse> wall = service.wall(30L, null, false, 0, 20);

        assertThat(wall.content()).extracting(WallPostResponse::id).containsExactly(9L);
    }

    @Test
    void aPostRestrictedByAModeratorDisappearsFromTheWallForEveryoneIncludingMembers() {
        // S3-B07: /posts/batch only returns PUBLISHED posts, so a RESTRICTED post is absent from its answer even
        // while its association is still CONFIRMED and even for a member asking for hidden posts.
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(9L, 7L, 5L));
        when(communityClient.findPublishedPosts(anyCollection())).thenReturn(List.of(post(9, NEW), post(5, OLD)));

        PageResponse<WallPostResponse> forVisitor = service.wall(30L, null, false, 0, 20);
        PageResponse<WallPostResponse> forMember = service.wall(30L, MEMBER, true, 0, 20);

        assertThat(forVisitor.content()).extracting(WallPostResponse::id).containsExactly(9L, 5L);
        assertThat(forMember.content()).extracting(WallPostResponse::id).containsExactly(9L, 5L);
    }

    @Test
    void anEmptyWallDoesNotCallCommunity() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of());

        PageResponse<WallPostResponse> wall = service.wall(30L, null, false, 0, 20);

        assertThat(wall.content()).isEmpty();
        assertThat(wall.totalPages()).isZero();
        verifyNoInteractions(communityClient);
    }

    @Test
    void theWallOfAnUnknownPageIs404() {
        when(pages.findByIdAndStatus(31L, PageStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.wall(31L, null, false, 0, 20)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void locationOrCommunityDownIs503() {
        when(locationClient.confirmedPostIds(14L))
                .thenThrow(new DependencyUnavailableException("LOCATION_SERVICE_UNAVAILABLE", "down", null));
        assertThatThrownBy(() -> service.wall(30L, null, false, 0, 20))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void communityDownIs503() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(9L));
        when(communityClient.findPublishedPosts(anyCollection()))
                .thenThrow(new DependencyUnavailableException("COMMUNITY_SERVICE_UNAVAILABLE", "down", null));

        assertThatThrownBy(() -> service.wall(30L, null, false, 0, 20))
                .isInstanceOf(DependencyUnavailableException.class)
                .extracting("code").isEqualTo("COMMUNITY_SERVICE_UNAVAILABLE");
    }

    // ----------------------------------------------------------------- hide / unhide (S3-US06)

    @Test
    void aMemberHidesAPostFromTheWallOnlyAuditedAndCommunityIsNeverCalled() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(5L, 9L));
        when(overrides.findByPageIdAndPostId(30L, 5L)).thenReturn(Optional.empty());
        when(overrides.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WallVisibilityResponse response = service.hide(30L, 5L, " Hors sujet ", MEMBER);

        assertThat(response.hidden()).isTrue();
        assertThat(response.reason()).isEqualTo("Hors sujet");
        assertThat(response.actorId()).isEqualTo("user-1");
        verify(audit).record(AuditAction.POST_HIDDEN, 30L, null, 5L, MEMBER, "Hors sujet");
        verifyNoInteractions(communityClient);
    }

    @Test
    void aNonMemberCannotHide403() {
        assertThatThrownBy(() -> service.hide(30L, 5L, null, OTHER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.hide(30L, 5L, null, ADMIN)).isInstanceOf(ForbiddenException.class);
        verify(overrides, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void hidingAPostThatIsNotOnTheWallIs404() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(9L));

        assertThatThrownBy(() -> service.hide(30L, 5L, null, MEMBER)).isInstanceOf(EntityNotFoundException.class);
        verify(overrides, never()).save(any());
    }

    @Test
    void hidingAHiddenPostIs409() {
        when(locationClient.confirmedPostIds(14L)).thenReturn(List.of(5L));
        when(overrides.findByPageIdAndPostId(30L, 5L)).thenReturn(Optional.of(hidden(5)));

        assertThatThrownBy(() -> service.hide(30L, 5L, null, MEMBER)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aMemberUnhidesAndItIsAudited() {
        when(overrides.findByPageIdAndPostId(30L, 5L)).thenReturn(Optional.of(hidden(5)));
        when(overrides.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WallVisibilityResponse response = service.unhide(30L, 5L, MEMBER);

        assertThat(response.hidden()).isFalse();
        verify(audit).record(AuditAction.POST_UNHIDDEN, 30L, null, 5L, MEMBER, null);
        verifyNoInteractions(communityClient);
    }

    @Test
    void unhidingAPostThatIsNotHiddenIs409AndANonMember403() {
        when(overrides.findByPageIdAndPostId(30L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unhide(30L, 5L, MEMBER)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.unhide(30L, 5L, OTHER)).isInstanceOf(ForbiddenException.class);
    }

    // ----------------------------------------------------------------- history

    @Test
    void theHistoryIsForMembersAndAdministrators() {
        when(auditEntries.findAllByPageIdAndActionIn(eq(30L), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.history(30L, MEMBER, 0, 20).totalElements()).isZero();
        assertThat(service.history(30L, ADMIN, 0, 20).totalElements()).isZero();
        assertThatThrownBy(() -> service.history(30L, OTHER, 0, 20)).isInstanceOf(ForbiddenException.class);
    }
}

package com.tounsiguide.communityservice.report;

import com.tounsiguide.communityservice.comment.Comment;
import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.common.error.ConflictException;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PublicationStatus;
import com.tounsiguide.communityservice.report.dto.ReportDtos.CreateReportRequest;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ModerationDecisionResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ReportResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ReportedContentResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.RestrictedContentResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final CurrentUser AUTHOR = new CurrentUser("author-1", "Sarra", Set.of("MEMBER"));
    private static final CurrentUser MEMBER = new CurrentUser("user-2", "Ali", Set.of("MEMBER"));
    private static final CurrentUser OTHER = new CurrentUser("user-3", "Karim", Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", Set.of("MEMBER", "ADMIN"));

    @Mock
    private ReportRepository reports;
    @Mock
    private ModerationDecisionRepository decisions;
    @Mock
    private PostRepository posts;
    @Mock
    private CommentRepository comments;
    @Mock
    private PostChangeNotifier changeNotifier;

    private ReportService service;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, decisions, posts, comments, changeNotifier);
        post = new Post("Bon café à Tunis", "author-1", "Sarra", 14L);
        setField(post, "id", 12L);
        comment = new Comment(post, "Arnaque, n'y allez pas", "author-1", "Sarra");
        setField(comment, "id", 88L);
        lenient().when(posts.findById(12L)).thenReturn(Optional.of(post));
        lenient().when(comments.findById(88L)).thenReturn(Optional.of(comment));
        lenient().when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Report open(ReportTargetType type, long targetId, String reporter, ReportReason reason, String at) {
        Report report = new Report(type, targetId, 12L, reporter, reporter, reason, null);
        setField(report, "createdAt", Instant.parse(at));
        return report;
    }

    // ----------------------------------------------------------------- reporting (S3-US07)

    @Test
    void aMemberReportsAPostAndNothingIsRemoved() {
        ReportResponse response = service.report(
                new CreateReportRequest(ReportTargetType.POST, 12L, ReportReason.SPAM, "  Lien publicitaire "), MEMBER);

        assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.reason()).isEqualTo(ReportReason.SPAM);
        assertThat(response.detail()).isEqualTo("Lien publicitaire");
        assertThat(response.reporterName()).isEqualTo("Ali");
        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void aCommentReportKeepsItsPost() {
        ReportResponse response = service.report(
                new CreateReportRequest(ReportTargetType.COMMENT, 88L, ReportReason.OFFENSIVE, null), MEMBER);

        assertThat(response.targetType()).isEqualTo(ReportTargetType.COMMENT);
        assertThat(response.postId()).isEqualTo(12L);
    }

    @Test
    void reportingOnesOwnContentIs403() {
        assertThatThrownBy(() -> service.report(
                new CreateReportRequest(ReportTargetType.POST, 12L, ReportReason.OTHER, null), AUTHOR))
                .isInstanceOf(ForbiddenException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void aSecondOpenReportBySameMemberOnSameContentIs409() {
        when(reports.existsByTargetTypeAndTargetIdAndReporterIdAndStatus(ReportTargetType.POST, 12L, "user-2",
                ReportStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.report(
                new CreateReportRequest(ReportTargetType.POST, 12L, ReportReason.SPAM, null), MEMBER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reportingAMissingDeletedOrRestrictedContentIs404() {
        when(posts.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(
                new CreateReportRequest(ReportTargetType.POST, 99L, ReportReason.SPAM, null), MEMBER))
                .isInstanceOf(EntityNotFoundException.class);

        post.markRestricted("admin-1", "x");
        assertThatThrownBy(() -> service.report(
                new CreateReportRequest(ReportTargetType.POST, 12L, ReportReason.SPAM, null), MEMBER))
                .isInstanceOf(EntityNotFoundException.class);
        // A comment of a restricted post cannot be reported either.
        assertThatThrownBy(() -> service.report(
                new CreateReportRequest(ReportTargetType.COMMENT, 88L, ReportReason.SPAM, null), MEMBER))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ----------------------------------------------------------------- queue

    @Test
    void theQueueIsForModeratorsOnly() {
        assertThatThrownBy(() -> service.queue(0, 20, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.restricted(0, 20, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.dismiss(ReportTargetType.POST, 12L, null, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.restrict(ReportTargetType.POST, 12L, "x", MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.restore(ReportTargetType.POST, 12L, null, MEMBER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reports);
    }

    @Test
    void theQueueGroupsOpenReportsByContentMostReportedFirstWithReasonCounts() {
        when(reports.findAllByStatusOrderByCreatedAtAsc(ReportStatus.OPEN)).thenReturn(List.of(
                open(ReportTargetType.COMMENT, 88L, "user-2", ReportReason.OFFENSIVE, "2026-11-15T09:00:00Z"),
                open(ReportTargetType.POST, 12L, "user-2", ReportReason.SPAM, "2026-11-15T10:00:00Z"),
                open(ReportTargetType.POST, 12L, "user-3", ReportReason.SPAM, "2026-11-15T11:00:00Z"),
                open(ReportTargetType.POST, 12L, "user-4", ReportReason.FALSE_INFO, "2026-11-15T12:00:00Z")));

        PageResponse<ReportedContentResponse> queue = service.queue(0, 20, ADMIN);

        assertThat(queue.totalElements()).isEqualTo(2);
        ReportedContentResponse top = queue.content().get(0);
        assertThat(top.targetType()).isEqualTo(ReportTargetType.POST);
        assertThat(top.openReports()).isEqualTo(3);
        assertThat(top.reasons()).containsEntry(ReportReason.SPAM, 2L).containsEntry(ReportReason.FALSE_INFO, 1L);
        assertThat(top.preview()).isEqualTo("Bon café à Tunis");
        assertThat(top.authorName()).isEqualTo("Sarra");
        assertThat(top.contentStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(top.lastReportedAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
        assertThat(queue.content().get(1).targetType()).isEqualTo(ReportTargetType.COMMENT);
    }

    @Test
    void aLongContentIsCutInThePreview() {
        assertThat(ReportService.preview("a".repeat(400))).hasSize(ReportService.PREVIEW_LENGTH).endsWith("…");
    }

    // ----------------------------------------------------------------- decisions (S3-US08)

    @Test
    void dismissingClosesEveryOpenReportOfTheContentAndRecordsTheDecision() {
        List<Report> open = List.of(open(ReportTargetType.POST, 12L, "user-2", ReportReason.SPAM, "2026-11-15T10:00:00Z"),
                open(ReportTargetType.POST, 12L, "user-3", ReportReason.SPAM, "2026-11-15T11:00:00Z"));
        when(reports.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 12L, ReportStatus.OPEN)).thenReturn(open);

        ModerationDecisionResponse decision = service.dismiss(ReportTargetType.POST, 12L, " Rien d'anormal ", ADMIN);

        assertThat(open).allMatch(r -> r.getStatus() == ReportStatus.DISMISSED && "admin-1".equals(r.getDecidedBy()));
        assertThat(decision.action()).isEqualTo(ModerationAction.DISMISS);
        assertThat(decision.reportsClosed()).isEqualTo(2);
        assertThat(decision.reason()).isEqualTo("Rien d'anormal");
        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void dismissingWithoutOpenReportsIs409() {
        when(reports.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 12L, ReportStatus.OPEN)).thenReturn(List.of());

        assertThatThrownBy(() -> service.dismiss(ReportTargetType.POST, 12L, null, ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void restrictingNeedsAReason() {
        assertThatThrownBy(() -> service.restrict(ReportTargetType.POST, 12L, "  ", ADMIN))
                .isInstanceOf(InvalidParameterException.class);
        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    void restrictingAPostHidesItPlatformWideClosesReportsAndPingsTheLocationService() {
        List<Report> open = List.of(open(ReportTargetType.POST, 12L, "user-2", ReportReason.SPAM, "2026-11-15T10:00:00Z"));
        when(reports.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 12L, ReportStatus.OPEN)).thenReturn(open);

        ModerationDecisionResponse decision = service.restrict(ReportTargetType.POST, 12L, "Publicité", ADMIN);

        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.RESTRICTED);
        assertThat(post.getRestrictionReason()).isEqualTo("Publicité");
        assertThat(open.get(0).getStatus()).isEqualTo(ReportStatus.ACTIONED);
        assertThat(decision.action()).isEqualTo(ModerationAction.RESTRICT);
        assertThat(decision.reportsClosed()).isEqualTo(1);
        verify(changeNotifier).postChanged(12L);
        ArgumentCaptor<ModerationDecision> saved = ArgumentCaptor.forClass(ModerationDecision.class);
        verify(decisions).save(saved.capture());
        assertThat(saved.getValue().getActorId()).isEqualTo("admin-1");
    }

    @Test
    void restrictingACommentPingsItsPost() {
        when(reports.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType.COMMENT, 88L, ReportStatus.OPEN)).thenReturn(List.of());

        service.restrict(ReportTargetType.COMMENT, 88L, "Insultes", ADMIN);

        assertThat(comment.isRestricted()).isTrue();
        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        verify(changeNotifier).postChanged(12L);
    }

    @Test
    void restrictingTwiceIs409AndADeletedPostIs404() {
        post.markRestricted("admin-1", "x");
        assertThatThrownBy(() -> service.restrict(ReportTargetType.POST, 12L, "y", ADMIN)).isInstanceOf(ConflictException.class);

        Post deleted = new Post("x", "a", "A", null);
        deleted.markDeleted("a");
        when(posts.findById(13L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.restrict(ReportTargetType.POST, 13L, "y", ADMIN)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void restoringPublishesAgainAndIsRecorded() {
        post.markRestricted("admin-1", "Publicité");

        ModerationDecisionResponse decision = service.restore(ReportTargetType.POST, 12L, "Erreur de modération", ADMIN);

        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(post.getRestrictionReason()).isNull();
        assertThat(decision.action()).isEqualTo(ModerationAction.RESTORE);
        verify(changeNotifier).postChanged(12L);
    }

    @Test
    void restoringSomethingNotRestrictedIs409() {
        assertThatThrownBy(() -> service.restore(ReportTargetType.POST, 12L, null, ADMIN)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.restore(ReportTargetType.COMMENT, 88L, null, ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void theRestrictedListHasPostsAndComments() {
        post.markRestricted("admin-1", "Publicité");
        comment.markRestricted("admin-1", "Insultes");
        when(posts.findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus.RESTRICTED)).thenReturn(List.of(post));
        when(comments.findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus.RESTRICTED)).thenReturn(List.of(comment));

        PageResponse<RestrictedContentResponse> list = service.restricted(0, 20, ADMIN);

        assertThat(list.totalElements()).isEqualTo(2);
        assertThat(list.content()).extracting(RestrictedContentResponse::restrictionReason)
                .containsExactlyInAnyOrder("Publicité", "Insultes");
    }
}

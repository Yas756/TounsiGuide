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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reporting and moderation (S3-US07, S3-US08, S3-B07; carry-over S1-US06).
 * <ul>
 *   <li>A member reports a post or a comment; nothing is removed automatically.</li>
 *   <li>An administrator (moderator) dismisses the open reports of a content, or restricts it platform-wide
 *       (status RESTRICTED: out of the feed, out of business walls through /posts/batch, 404 for the public).
 *       One decision closes every open report on that content. A restricted content can be restored.</li>
 *   <li>Every decision is written to {@code moderation_decisions}.</li>
 * </ul>
 * After a decision on a post (or one of its comments) the Location Service is pinged like after an edit: a
 * restricted post answers it 404, so its associations are flagged exactly like those of a deleted post, and the
 * flag is cleared on restore.
 */
@Service
public class ReportService {

    public static final int MAX_PAGE_SIZE = 50;
    static final int PREVIEW_LENGTH = 300;
    static final int MAX_REPORTS_SHOWN = 20;

    private final ReportRepository reports;
    private final ModerationDecisionRepository decisions;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostChangeNotifier changeNotifier;

    public ReportService(ReportRepository reports, ModerationDecisionRepository decisions, PostRepository posts,
                         CommentRepository comments, PostChangeNotifier changeNotifier) {
        this.reports = reports;
        this.decisions = decisions;
        this.posts = posts;
        this.comments = comments;
        this.changeNotifier = changeNotifier;
    }

    // ------------------------------------------------------------------ member

    /**
     * @throws EntityNotFoundException 404 when the content does not exist or is not published
     * @throws ForbiddenException      403 when it is the reporter's own content
     * @throws ConflictException       409 when this member already has an open report on it
     */
    @Transactional
    public ReportResponse report(CreateReportRequest body, CurrentUser user) {
        Target target = visibleTarget(body.targetType(), body.targetId());
        if (target.authorId().equals(user.id())) {
            throw new ForbiddenException("Vous ne pouvez pas signaler votre propre contenu");
        }
        if (reports.existsByTargetTypeAndTargetIdAndReporterIdAndStatus(body.targetType(), body.targetId(), user.id(),
                ReportStatus.OPEN)) {
            throw new ConflictException("Vous avez déjà signalé ce contenu ; un modérateur va l'examiner");
        }
        String detail = body.detail() == null || body.detail().isBlank() ? null : body.detail().trim();
        Report saved = reports.save(new Report(body.targetType(), body.targetId(), target.postId(), user.id(),
                user.displayName(), body.reason(), detail));
        return ReportResponse.from(saved);
    }

    // ------------------------------------------------------------------ moderator reads

    /** Contents with open reports: most reported first, then the oldest report first. Grouped in memory (MVP). */
    @Transactional(readOnly = true)
    public PageResponse<ReportedContentResponse> queue(int page, int size, CurrentUser user) {
        requireModerator(user);
        Map<String, List<Report>> byTarget = new LinkedHashMap<>();
        for (Report report : reports.findAllByStatusOrderByCreatedAtAsc(ReportStatus.OPEN)) {
            byTarget.computeIfAbsent(report.getTargetType() + ":" + report.getTargetId(), key -> new ArrayList<>()).add(report);
        }
        List<List<Report>> groups = new ArrayList<>(byTarget.values());
        groups.sort(Comparator.<List<Report>>comparingInt(List::size).reversed()
                .thenComparing(group -> group.get(0).getCreatedAt()));
        return slice(groups, page, size, this::toQueueItem);
    }

    /** Restricted posts and comments, most recently restricted first, so they can be restored. */
    @Transactional(readOnly = true)
    public PageResponse<RestrictedContentResponse> restricted(int page, int size, CurrentUser user) {
        requireModerator(user);
        List<RestrictedContentResponse> all = new ArrayList<>();
        posts.findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus.RESTRICTED).forEach(post ->
                all.add(new RestrictedContentResponse(ReportTargetType.POST, post.getId(), post.getId(),
                        preview(post.getContent()), post.getAuthorName(), post.getRestrictedAt(), post.getRestrictionReason())));
        comments.findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus.RESTRICTED).forEach(comment ->
                all.add(new RestrictedContentResponse(ReportTargetType.COMMENT, comment.getId(), comment.getPost().getId(),
                        preview(comment.getContent()), comment.getAuthorName(), comment.getRestrictedAt(),
                        comment.getRestrictionReason())));
        all.sort(Comparator.comparing(RestrictedContentResponse::restrictedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return slice(all, page, size, item -> item);
    }

    @Transactional(readOnly = true)
    public List<ModerationDecisionResponse> history(ReportTargetType type, Long targetId, CurrentUser user) {
        requireModerator(user);
        return decisions.findAllByTargetTypeAndTargetIdOrderByIdDesc(type, targetId).stream()
                .map(ModerationDecisionResponse::from).toList();
    }

    // ------------------------------------------------------------------ moderator decisions

    /** The open reports were not founded: they are closed, the content stays as it is. 409 when none is open. */
    @Transactional
    public ModerationDecisionResponse dismiss(ReportTargetType type, Long targetId, String reason, CurrentUser user) {
        requireModerator(user);
        List<Report> open = reports.findAllByTargetTypeAndTargetIdAndStatus(type, targetId, ReportStatus.OPEN);
        if (open.isEmpty()) {
            throw new ConflictException("Aucun signalement ouvert sur ce contenu");
        }
        String cleanReason = clean(reason);
        open.forEach(report -> report.close(ReportStatus.DISMISSED, user.id(), user.displayName(), cleanReason));
        reports.saveAll(open);
        return record(type, targetId, ModerationAction.DISMISS, user, cleanReason, open.size());
    }

    /**
     * Restricts the content platform-wide (reason required) and closes its open reports as ACTIONED.
     * Also possible without open reports. 404 for a missing or deleted content, 409 when already restricted.
     */
    @Transactional
    public ModerationDecisionResponse restrict(ReportTargetType type, Long targetId, String reason, CurrentUser user) {
        requireModerator(user);
        String cleanReason = clean(reason);
        if (cleanReason == null) {
            throw new InvalidParameterException("reason", "Le motif de la restriction est obligatoire");
        }
        Long postId;
        if (type == ReportTargetType.POST) {
            Post post = posts.findById(targetId).filter(p -> !p.isDeleted())
                    .orElseThrow(() -> new EntityNotFoundException("Publication introuvable"));
            if (post.isRestricted()) {
                throw new ConflictException("Cette publication est déjà restreinte");
            }
            post.markRestricted(user.id(), cleanReason);
            posts.save(post);
            postId = post.getId();
        } else {
            Comment comment = comments.findById(targetId)
                    .orElseThrow(() -> new EntityNotFoundException("Commentaire introuvable"));
            if (comment.isRestricted()) {
                throw new ConflictException("Ce commentaire est déjà restreint");
            }
            comment.markRestricted(user.id(), cleanReason);
            comments.save(comment);
            postId = comment.getPost().getId();
        }
        List<Report> open = reports.findAllByTargetTypeAndTargetIdAndStatus(type, targetId, ReportStatus.OPEN);
        open.forEach(report -> report.close(ReportStatus.ACTIONED, user.id(), user.displayName(), cleanReason));
        reports.saveAll(open);
        changeNotifier.postChanged(postId);
        return record(type, targetId, ModerationAction.RESTRICT, user, cleanReason, open.size());
    }

    /** Publishes a restricted content again. 409 when it is not restricted. */
    @Transactional
    public ModerationDecisionResponse restore(ReportTargetType type, Long targetId, String reason, CurrentUser user) {
        requireModerator(user);
        Long postId;
        if (type == ReportTargetType.POST) {
            Post post = posts.findById(targetId).orElseThrow(() -> new EntityNotFoundException("Publication introuvable"));
            if (!post.isRestricted()) {
                throw new ConflictException("Cette publication n'est pas restreinte");
            }
            post.restore();
            posts.save(post);
            postId = post.getId();
        } else {
            Comment comment = comments.findById(targetId)
                    .orElseThrow(() -> new EntityNotFoundException("Commentaire introuvable"));
            if (!comment.isRestricted()) {
                throw new ConflictException("Ce commentaire n'est pas restreint");
            }
            comment.restore();
            comments.save(comment);
            postId = comment.getPost().getId();
        }
        changeNotifier.postChanged(postId);
        return record(type, targetId, ModerationAction.RESTORE, user, clean(reason), 0);
    }

    // ------------------------------------------------------------------ helpers

    private record Target(String authorId, Long postId) {
    }

    /** A published post, or a published comment of a published post; 404 otherwise. */
    private Target visibleTarget(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.POST) {
            Post post = posts.findById(targetId).filter(Post::isPublished)
                    .orElseThrow(() -> new EntityNotFoundException("Publication introuvable"));
            return new Target(post.getAuthorId(), post.getId());
        }
        Comment comment = comments.findById(targetId)
                .filter(c -> !c.isRestricted() && c.getPost().isPublished())
                .orElseThrow(() -> new EntityNotFoundException("Commentaire introuvable"));
        return new Target(comment.getAuthorId(), comment.getPost().getId());
    }

    private ReportedContentResponse toQueueItem(List<Report> group) {
        Report first = group.get(0);
        Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
        group.forEach(report -> reasons.merge(report.getReason(), 1L, Long::sum));
        Instant last = group.get(group.size() - 1).getCreatedAt();
        String preview = null;
        String authorId = null;
        String authorName = null;
        PublicationStatus status = null;
        if (first.getTargetType() == ReportTargetType.POST) {
            Optional<Post> post = posts.findById(first.getTargetId());
            if (post.isPresent()) {
                preview = preview(post.get().getContent());
                authorId = post.get().getAuthorId();
                authorName = post.get().getAuthorName();
                status = post.get().getPublicationStatus();
            }
        } else {
            Optional<Comment> comment = comments.findById(first.getTargetId());
            if (comment.isPresent()) {
                preview = preview(comment.get().getContent());
                authorId = comment.get().getAuthorId();
                authorName = comment.get().getAuthorName();
                status = comment.get().getPublicationStatus();
            }
        }
        return new ReportedContentResponse(first.getTargetType(), first.getTargetId(), first.getPostId(), preview,
                authorId, authorName, status, group.size(), reasons, first.getCreatedAt(), last,
                group.stream().limit(MAX_REPORTS_SHOWN).map(ReportResponse::from).toList());
    }

    private ModerationDecisionResponse record(ReportTargetType type, Long targetId, ModerationAction action,
                                              CurrentUser user, String reason, int closed) {
        return ModerationDecisionResponse.from(decisions.save(
                new ModerationDecision(type, targetId, action, user.id(), user.displayName(), reason, closed)));
    }

    private static void requireModerator(CurrentUser user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Action réservée aux modérateurs");
        }
    }

    private static <S, T> PageResponse<T> slice(List<S> all, int page, int size, java.util.function.Function<S, T> mapper) {
        int pageNumber = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int from = (int) Math.min((long) pageNumber * pageSize, all.size());
        List<T> content = all.subList(from, Math.min(all.size(), from + pageSize)).stream().map(mapper).toList();
        int totalPages = all.isEmpty() ? 0 : (all.size() + pageSize - 1) / pageSize;
        return new PageResponse<>(content, pageNumber, pageSize, all.size(), totalPages);
    }

    static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

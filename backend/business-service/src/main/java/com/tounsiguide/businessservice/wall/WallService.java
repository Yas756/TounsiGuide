package com.tounsiguide.businessservice.wall;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditEntryRepository;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.audit.dto.AuditEntryResponse;
import com.tounsiguide.businessservice.client.CommunityClient;
import com.tounsiguide.businessservice.client.CommunityClient.PostSnapshot;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.BusinessPage;
import com.tounsiguide.businessservice.page.PageAccess;
import com.tounsiguide.businessservice.wall.dto.WallPostResponse;
import com.tounsiguide.businessservice.wall.dto.WallVisibilityResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The wall of a page (S3-US05, S3-US06): every post with a CONFIRMED association to the page's Location (read
 * from the Location Service, so posts older than the page are included), with the post data read from the
 * Community Service. Nothing is copied into this service. A page member can hide a post from this wall only:
 * the post stays in the general feed and on its author's profile, and the Community Service is never called to
 * change it.
 */
@Service
public class WallService {

    private static final Set<AuditAction> WALL_ACTIONS = Set.of(AuditAction.POST_HIDDEN, AuditAction.POST_UNHIDDEN);

    private final PageAccess access;
    private final LocationClient locationClient;
    private final CommunityClient communityClient;
    private final WallVisibilityOverrideRepository overrides;
    private final AuditService audit;
    private final AuditEntryRepository auditEntries;

    public WallService(PageAccess access, LocationClient locationClient, CommunityClient communityClient,
                       WallVisibilityOverrideRepository overrides, AuditService audit,
                       AuditEntryRepository auditEntries) {
        this.access = access;
        this.locationClient = locationClient;
        this.communityClient = communityClient;
        this.overrides = overrides;
        this.audit = audit;
        this.auditEntries = auditEntries;
    }

    /**
     * One page of the wall, newest post first (post ids grow with creation time).
     * Hidden posts are left out, except for a page member who asks {@code includeHidden=true}: they then come
     * flagged {@code hidden=true} so that the member can unhide them. {@code includeHidden} is ignored for anyone
     * else.
     */
    @Transactional(readOnly = true)
    public PageResponse<WallPostResponse> wall(Long pageId, CurrentUser user, boolean includeHidden, int page, int size) {
        BusinessPage businessPage = access.activePage(pageId);
        boolean showHidden = includeHidden && access.isMember(pageId, user);
        Map<Long, WallVisibilityOverride> hidden = overrides.findAllByPageIdAndHiddenTrue(pageId).stream()
                .collect(Collectors.toMap(WallVisibilityOverride::getPostId, Function.identity(), (a, b) -> a));

        List<Long> ids = locationClient.confirmedPostIds(businessPage.getLocationId()).stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> showHidden || !hidden.containsKey(id))
                .sorted(Comparator.reverseOrder())
                .toList();

        int pageNumber = Paging.page(page);
        int pageSize = Paging.size(size);
        int from = (int) Math.min((long) pageNumber * pageSize, ids.size());
        List<Long> slice = ids.subList(from, Math.min(ids.size(), from + pageSize));

        Map<Long, PostSnapshot> posts = slice.isEmpty() ? Map.of()
                : communityClient.findPublishedPosts(slice).stream()
                .collect(Collectors.toMap(PostSnapshot::id, Function.identity(), (a, b) -> a));
        // A post deleted in the Community Service but not yet flagged in the Location Service is simply skipped.
        List<WallPostResponse> content = slice.stream()
                .filter(posts::containsKey)
                .map(id -> {
                    WallVisibilityOverride override = hidden.get(id);
                    return override == null
                            ? WallPostResponse.of(posts.get(id), false, null, null)
                            : WallPostResponse.of(posts.get(id), true, override.getReason(), override.getChangedAt());
                })
                .toList();
        int totalPages = ids.isEmpty() ? 0 : (ids.size() + pageSize - 1) / pageSize;
        return new PageResponse<>(content, pageNumber, pageSize, ids.size(), totalPages);
    }

    /**
     * Hides a post from this wall only. Page members only (403); the post must be on the wall (404); hiding a
     * hidden post answers 409.
     */
    @Transactional
    public WallVisibilityResponse hide(Long pageId, Long postId, String reason, CurrentUser user) {
        BusinessPage page = access.activePage(pageId);
        access.requireMember(pageId, user, "Seuls les membres de cette page peuvent gérer son mur");
        if (!locationClient.confirmedPostIds(page.getLocationId()).contains(postId)) {
            throw new EntityNotFoundException("Cette publication n'est pas sur le mur de la page");
        }
        WallVisibilityOverride override = overrides.findByPageIdAndPostId(pageId, postId)
                .orElseGet(() -> new WallVisibilityOverride(pageId, postId));
        if (override.isHidden()) {
            throw new ConflictException("Cette publication est déjà masquée sur ce mur");
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        override.set(true, cleanReason, user.id(), user.displayName());
        WallVisibilityOverride saved = overrides.save(override);
        audit.record(AuditAction.POST_HIDDEN, pageId, null, postId, user, cleanReason);
        return WallVisibilityResponse.from(saved);
    }

    /** Shows a hidden post again. Page members only (403); 409 when the post is not hidden on this wall. */
    @Transactional
    public WallVisibilityResponse unhide(Long pageId, Long postId, CurrentUser user) {
        access.activePage(pageId);
        access.requireMember(pageId, user, "Seuls les membres de cette page peuvent gérer son mur");
        WallVisibilityOverride override = overrides.findByPageIdAndPostId(pageId, postId)
                .filter(WallVisibilityOverride::isHidden)
                .orElseThrow(() -> new ConflictException("Cette publication n'est pas masquée sur ce mur"));
        override.set(false, null, user.id(), user.displayName());
        WallVisibilityOverride saved = overrides.save(override);
        audit.record(AuditAction.POST_UNHIDDEN, pageId, null, postId, user, null);
        return WallVisibilityResponse.from(saved);
    }

    /** Hide/unhide history of the wall, newest first: page members and administrators. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> history(Long pageId, CurrentUser user, int page, int size) {
        access.activePage(pageId);
        if (!user.isAdmin() && !access.isMember(pageId, user)) {
            throw new ForbiddenException("Historique réservé aux membres de la page et aux administrateurs");
        }
        return PageResponse.of(auditEntries.findAllByPageIdAndActionIn(pageId, WALL_ACTIONS,
                Paging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))),
                AuditEntryResponse::from);
    }
}

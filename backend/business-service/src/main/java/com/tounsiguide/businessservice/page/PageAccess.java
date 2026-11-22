package com.tounsiguide.businessservice.page;

import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Page membership checks (S3-B06). Membership is read from PageMember, never deduced from the global role
 * BUSINESS_REP: a representative of one page has no right on another page, and an administrator is not a member.
 */
@Component
public class PageAccess {

    private final BusinessPageRepository pages;
    private final PageMemberRepository members;

    public PageAccess(BusinessPageRepository pages, PageMemberRepository members) {
        this.pages = pages;
        this.members = members;
    }

    /** @throws EntityNotFoundException 404 when the page does not exist or was deleted */
    public BusinessPage activePage(Long pageId) {
        return pages.findByIdAndStatus(pageId, PageStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Page introuvable"));
    }

    /** The caller's role on the page, or null (visitor, other member, administrator). */
    public PageRole roleOf(Long pageId, CurrentUser user) {
        if (user == null) {
            return null;
        }
        return members.findByPageIdAndUserId(pageId, user.id()).map(PageMember::getRole).orElse(null);
    }

    public boolean isMember(Long pageId, CurrentUser user) {
        return roleOf(pageId, user) != null;
    }

    /** @throws ForbiddenException 403 when the caller is not a member of this page */
    public void requireMember(Long pageId, CurrentUser user, String message) {
        if (!isMember(pageId, user)) {
            throw new ForbiddenException(message);
        }
    }
}

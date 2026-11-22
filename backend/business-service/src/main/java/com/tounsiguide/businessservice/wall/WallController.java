package com.tounsiguide.businessservice.wall;

import com.tounsiguide.businessservice.audit.dto.AuditEntryResponse;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.wall.dto.HideRequest;
import com.tounsiguide.businessservice.wall.dto.WallPostResponse;
import com.tounsiguide.businessservice.wall.dto.WallVisibilityResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-pages/{pageId}/wall")
public class WallController {

    private final WallService service;

    public WallController(WallService service) {
        this.service = service;
    }

    /** Public. {@code includeHidden=true} is only honoured for members of the page. */
    @GetMapping
    public PageResponse<WallPostResponse> wall(
            @PathVariable Long pageId,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId,
            @RequestHeader(name = CurrentUser.NAME_HEADER, required = false) String userName,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles
    ) {
        return service.wall(pageId, CurrentUser.fromHeadersOrNull(userId, userName, roles), includeHidden, page, size);
    }

    @PostMapping("/{postId}/hide")
    public WallVisibilityResponse hide(
            @PathVariable Long pageId,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) HideRequest body,
            CurrentUser user
    ) {
        return service.hide(pageId, postId, body == null ? null : body.reason(), user);
    }

    @PostMapping("/{postId}/unhide")
    public WallVisibilityResponse unhide(@PathVariable Long pageId, @PathVariable Long postId, CurrentUser user) {
        return service.unhide(pageId, postId, user);
    }

    @GetMapping("/history")
    public PageResponse<AuditEntryResponse> history(
            @PathVariable Long pageId,
            CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size
    ) {
        return service.history(pageId, user, page, size);
    }
}

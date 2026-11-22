package com.tounsiguide.businessservice.page;

import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.page.dto.BusinessPageResponse;
import com.tounsiguide.businessservice.page.dto.UpdatePageRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Business pages. Reading is public (the caller, if logged in, is told whether they are a member). */
@RestController
@RequestMapping("/api/v1/business-pages")
public class BusinessPageController {

    private final BusinessPageService service;

    public BusinessPageController(BusinessPageService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<BusinessPageResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size
    ) {
        return service.list(q, page, size);
    }

    @GetMapping("/{id}")
    public BusinessPageResponse get(
            @PathVariable Long id,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId,
            @RequestHeader(name = CurrentUser.NAME_HEADER, required = false) String userName,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles
    ) {
        return service.get(id, CurrentUser.fromHeadersOrNull(userId, userName, roles));
    }

    @GetMapping("/by-location/{locationId}")
    public BusinessPageResponse byLocation(
            @PathVariable Long locationId,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId,
            @RequestHeader(name = CurrentUser.NAME_HEADER, required = false) String userName,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles
    ) {
        return service.byLocation(locationId, CurrentUser.fromHeadersOrNull(userId, userName, roles));
    }

    @PutMapping("/{id}")
    public BusinessPageResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePageRequest body,
                                       CurrentUser user) {
        return service.update(id, body, user);
    }
}

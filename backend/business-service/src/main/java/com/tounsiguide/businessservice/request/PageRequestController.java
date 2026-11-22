package com.tounsiguide.businessservice.request;

import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.request.dto.CreatePageRequest;
import com.tounsiguide.businessservice.request.dto.PageRequestResponse;
import com.tounsiguide.businessservice.request.dto.RejectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Page requests: submitted by a logged-in member, decided by an administrator. */
@RestController
@RequestMapping("/api/v1/business-pages/requests")
public class PageRequestController {

    private final PageRequestService service;

    public PageRequestController(PageRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PageRequestResponse> submit(@Valid @RequestBody CreatePageRequest body, CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(body, user));
    }

    @GetMapping("/mine")
    public PageResponse<PageRequestResponse> mine(
            CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size
    ) {
        return service.mine(user, page, size);
    }

    /** Admin only. {@code ?status=PENDING} for the verification queue; no status = every request. */
    @GetMapping
    public PageResponse<PageRequestResponse> list(
            CurrentUser user,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size
    ) {
        return service.list(status, page, size, user);
    }

    @GetMapping("/{id}")
    public PageRequestResponse get(@PathVariable Long id, CurrentUser user) {
        return service.get(id, user);
    }

    @PostMapping("/{id}/approve")
    public PageRequestResponse approve(@PathVariable Long id, CurrentUser user) {
        return service.approve(id, user);
    }

    @PostMapping("/{id}/reject")
    public PageRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body, CurrentUser user) {
        return service.reject(id, body.reason(), user);
    }
}

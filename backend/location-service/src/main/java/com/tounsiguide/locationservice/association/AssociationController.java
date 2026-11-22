package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.association.dto.AdminSetAssociationRequest;
import com.tounsiguide.locationservice.association.dto.AssociationResponse;
import com.tounsiguide.locationservice.association.dto.DecisionResponse;
import com.tounsiguide.locationservice.association.dto.NoteRequest;
import com.tounsiguide.locationservice.association.dto.PageResponse;
import com.tounsiguide.locationservice.association.dto.PostAssociationSummary;
import com.tounsiguide.locationservice.association.dto.ReviewRequest;
import com.tounsiguide.locationservice.association.dto.SuggestAssociationRequest;
import com.tounsiguide.locationservice.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Association endpoints (association-api.md, section 6). Reading is public, writing needs a login,
 * review needs ADMIN. The Gateway has already checked the token.
 */
@RestController
public class AssociationController {

    private final AssociationService service;

    public AssociationController(AssociationService service) {
        this.service = service;
    }

    // ----- public and member

    @GetMapping("/api/v1/posts/{postId}/associations")
    public List<AssociationResponse> listForPost(@PathVariable Long postId) {
        return service.listForPost(postId);
    }

    @GetMapping("/api/v1/associations/summary")
    public Map<String, PostAssociationSummary> summary(@RequestParam List<Long> postIds) {
        return service.summary(postIds);
    }

    /** 201 for a new association, 200 when evidence was added to an existing one. */
    @PostMapping("/api/v1/posts/{postId}/associations")
    public ResponseEntity<AssociationResponse> suggest(
            @PathVariable Long postId,
            @Valid @RequestBody SuggestAssociationRequest request,
            CurrentUser user
    ) {
        AssociationService.SuggestResult result = service.suggest(postId, request, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.association());
    }

    @PostMapping("/api/v1/associations/{id}/confirm")
    public AssociationResponse confirm(@PathVariable Long id, CurrentUser user) {
        return service.confirm(id, user);
    }

    @PostMapping("/api/v1/associations/{id}/reject")
    public AssociationResponse reject(@PathVariable Long id, CurrentUser user) {
        return service.reject(id, user);
    }

    @PostMapping("/api/v1/associations/{id}/clarify")
    public AssociationResponse clarify(@PathVariable Long id, @Valid @RequestBody NoteRequest request, CurrentUser user) {
        return service.clarify(id, request.note(), user);
    }

    @PostMapping("/api/v1/associations/{id}/dispute")
    public AssociationResponse dispute(@PathVariable Long id, @Valid @RequestBody NoteRequest request, CurrentUser user) {
        return service.dispute(id, request.note(), user);
    }

    // ----- admin

    @GetMapping("/api/v1/associations")
    public PageResponse<AssociationResponse> queue(
            @RequestParam(required = false) List<Status> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            CurrentUser user
    ) {
        return service.queue(status, page, size, user);
    }

    @GetMapping("/api/v1/associations/{id}")
    public AssociationResponse get(@PathVariable Long id, CurrentUser user) {
        return service.get(id, user);
    }

    @GetMapping("/api/v1/associations/{id}/history")
    public List<DecisionResponse> history(@PathVariable Long id, CurrentUser user) {
        return service.history(id, user);
    }

    @PostMapping("/api/v1/associations/{id}/review")
    public AssociationResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request, CurrentUser user) {
        return service.review(id, request, user);
    }

    @PostMapping("/api/v1/posts/{postId}/associations/admin")
    public ResponseEntity<AssociationResponse> adminSet(
            @PathVariable Long postId,
            @Valid @RequestBody AdminSetAssociationRequest request,
            CurrentUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adminSet(postId, request, user));
    }

    @PostMapping("/api/v1/posts/{postId}/associations/rescan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void rescan(@PathVariable Long postId, CurrentUser user) {
        service.rescan(postId, user);
    }

    // ----- Location wall (Sprint 3)

    @GetMapping("/api/v1/locations/{locationId}/posts")
    public PageResponse<Long> postsOfLocation(
            @PathVariable Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.confirmedPostIds(locationId, page, size);
    }
}

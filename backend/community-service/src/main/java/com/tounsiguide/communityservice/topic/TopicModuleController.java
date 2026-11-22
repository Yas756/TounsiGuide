package com.tounsiguide.communityservice.topic;

import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ModuleDecisionRequest;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ModuleResponse;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ProposeModuleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Topic modules (Sprint 3, S3-B08). Posts of a module: GET /api/v1/posts?module={slug}. */
@RestController
@RequestMapping("/api/v1/modules")
public class TopicModuleController {

    private final TopicModuleService service;

    public TopicModuleController(TopicModuleService service) {
        this.service = service;
    }

    /** Public: approved modules by name, with their number of published posts. */
    @GetMapping
    public List<ModuleResponse> approved() {
        return service.approved();
    }

    @GetMapping("/mine")
    public List<ModuleResponse> mine(CurrentUser user) {
        return service.mine(user);
    }

    /** Admin: modules with a status (default PROPOSED), paged. */
    @GetMapping("/queue")
    public PageResponse<ModuleResponse> queue(
            CurrentUser user,
            @RequestParam(defaultValue = "PROPOSED") ModuleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.byStatus(status, page, size, user);
    }

    /** Public for approved modules; an administrator also sees the others. */
    @GetMapping("/{slug}")
    public ModuleResponse bySlug(
            @PathVariable String slug,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles
    ) {
        return service.bySlug(slug, CurrentUser.isAdminRoles(roles));
    }

    @PostMapping
    public ResponseEntity<ModuleResponse> propose(@Valid @RequestBody ProposeModuleRequest body, CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.propose(body, user));
    }

    @PostMapping("/{id}/approve")
    public ModuleResponse approve(@PathVariable Long id, CurrentUser user) {
        return service.approve(id, user);
    }

    @PostMapping("/{id}/reject")
    public ModuleResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) ModuleDecisionRequest body,
                                 CurrentUser user) {
        return service.reject(id, body == null ? null : body.reason(), user);
    }

    @PostMapping("/{id}/archive")
    public ModuleResponse archive(@PathVariable Long id, @Valid @RequestBody(required = false) ModuleDecisionRequest body,
                                  CurrentUser user) {
        return service.archive(id, body == null ? null : body.reason(), user);
    }
}

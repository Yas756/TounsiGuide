package com.tounsiguide.communityservice.report;

import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.report.dto.ReportDtos.CreateReportRequest;
import com.tounsiguide.communityservice.report.dto.ReportDtos.DecisionRequest;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ModerationDecisionResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ReportResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ReportedContentResponse;
import com.tounsiguide.communityservice.report.dto.ReportDtos.RestrictedContentResponse;
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

import java.util.List;

/** Reports (members) and moderation (administrators), Sprint 3 S3-B07. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> report(@Valid @RequestBody CreateReportRequest body, CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.report(body, user));
    }

    @GetMapping("/queue")
    public PageResponse<ReportedContentResponse> queue(
            CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.queue(page, size, user);
    }

    @GetMapping("/restricted")
    public PageResponse<RestrictedContentResponse> restricted(
            CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.restricted(page, size, user);
    }

    @PostMapping("/targets/{targetType}/{targetId}/dismiss")
    public ModerationDecisionResponse dismiss(@PathVariable ReportTargetType targetType, @PathVariable Long targetId,
                                              @Valid @RequestBody(required = false) DecisionRequest body, CurrentUser user) {
        return service.dismiss(targetType, targetId, body == null ? null : body.reason(), user);
    }

    @PostMapping("/targets/{targetType}/{targetId}/restrict")
    public ModerationDecisionResponse restrict(@PathVariable ReportTargetType targetType, @PathVariable Long targetId,
                                               @Valid @RequestBody(required = false) DecisionRequest body, CurrentUser user) {
        return service.restrict(targetType, targetId, body == null ? null : body.reason(), user);
    }

    @PostMapping("/targets/{targetType}/{targetId}/restore")
    public ModerationDecisionResponse restore(@PathVariable ReportTargetType targetType, @PathVariable Long targetId,
                                              @Valid @RequestBody(required = false) DecisionRequest body, CurrentUser user) {
        return service.restore(targetType, targetId, body == null ? null : body.reason(), user);
    }

    @GetMapping("/targets/{targetType}/{targetId}/history")
    public List<ModerationDecisionResponse> history(@PathVariable ReportTargetType targetType,
                                                    @PathVariable Long targetId, CurrentUser user) {
        return service.history(targetType, targetId, user);
    }
}

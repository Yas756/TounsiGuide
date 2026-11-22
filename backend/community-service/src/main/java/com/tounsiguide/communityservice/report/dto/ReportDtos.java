package com.tounsiguide.communityservice.report.dto;

import com.tounsiguide.communityservice.post.PublicationStatus;
import com.tounsiguide.communityservice.report.ModerationAction;
import com.tounsiguide.communityservice.report.ModerationDecision;
import com.tounsiguide.communityservice.report.Report;
import com.tounsiguide.communityservice.report.ReportReason;
import com.tounsiguide.communityservice.report.ReportStatus;
import com.tounsiguide.communityservice.report.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response bodies of /api/v1/reports (docs/api/community-service-api.md, "Reports and moderation"). */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** Body of POST /api/v1/reports. */
    public record CreateReportRequest(
            @NotNull(message = "Le type de contenu est obligatoire") ReportTargetType targetType,
            @NotNull(message = "Le contenu est obligatoire") @Positive(message = "Identifiant invalide") Long targetId,
            @NotNull(message = "Choisissez un motif") ReportReason reason,
            @Size(max = 1_000, message = "1000 caractères au maximum") String detail
    ) {
    }

    /** Body of restrict (reason required, checked by the service) and of dismiss/restore (reason optional). */
    public record DecisionRequest(
            @Size(max = 1_000, message = "1000 caractères au maximum") String reason
    ) {
    }

    public record ReportResponse(
            Long id,
            ReportTargetType targetType,
            Long targetId,
            Long postId,
            String reporterName,
            ReportReason reason,
            String detail,
            ReportStatus status,
            Instant createdAt,
            String decidedByName,
            Instant decidedAt,
            String decisionReason
    ) {
        public static ReportResponse from(Report report) {
            return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(), report.getPostId(),
                    report.getReporterName(), report.getReason(), report.getDetail(), report.getStatus(),
                    report.getCreatedAt(), report.getDecidedByName(), report.getDecidedAt(), report.getDecisionReason());
        }
    }

    /** One reported content in the moderation queue, with its open reports counted by reason. */
    public record ReportedContentResponse(
            ReportTargetType targetType,
            Long targetId,
            Long postId,
            /** Text of the post or comment, cut to 300 characters; null if the content no longer exists. */
            String preview,
            String authorId,
            String authorName,
            PublicationStatus contentStatus,
            long openReports,
            Map<ReportReason, Long> reasons,
            Instant firstReportedAt,
            Instant lastReportedAt,
            /** The open reports, oldest first (at most 20). */
            List<ReportResponse> reports
    ) {
    }

    /** A restricted post or comment, for the "restore" list. */
    public record RestrictedContentResponse(
            ReportTargetType targetType,
            Long targetId,
            Long postId,
            String preview,
            String authorName,
            Instant restrictedAt,
            String restrictionReason
    ) {
    }

    public record ModerationDecisionResponse(
            Long id,
            ReportTargetType targetType,
            Long targetId,
            ModerationAction action,
            String actorName,
            String reason,
            int reportsClosed,
            Instant createdAt
    ) {
        public static ModerationDecisionResponse from(ModerationDecision decision) {
            return new ModerationDecisionResponse(decision.getId(), decision.getTargetType(), decision.getTargetId(),
                    decision.getAction(), decision.getActorName(), decision.getReason(), decision.getReportsClosed(),
                    decision.getCreatedAt());
        }
    }
}

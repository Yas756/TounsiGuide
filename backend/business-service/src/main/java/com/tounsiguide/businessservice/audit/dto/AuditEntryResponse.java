package com.tounsiguide.businessservice.audit.dto;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditEntry;

import java.time.Instant;

public record AuditEntryResponse(
        Long id,
        AuditAction action,
        Long pageId,
        Long requestId,
        Long postId,
        String actorId,
        String actorName,
        String reason,
        Instant createdAt
) {

    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.getId(), entry.getAction(), entry.getPageId(), entry.getRequestId(),
                entry.getPostId(), entry.getActorId(), entry.getActorName(), entry.getReason(), entry.getCreatedAt());
    }
}

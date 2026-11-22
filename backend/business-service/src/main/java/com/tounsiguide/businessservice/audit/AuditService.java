package com.tounsiguide.businessservice.audit;

import com.tounsiguide.businessservice.common.security.CurrentUser;
import org.springframework.stereotype.Service;

/** Writes the append-only history, in the caller's transaction (no audit row without the change, and the reverse). */
@Service
public class AuditService {

    private final AuditEntryRepository repository;

    public AuditService(AuditEntryRepository repository) {
        this.repository = repository;
    }

    public void record(AuditAction action, Long pageId, Long requestId, Long postId, CurrentUser actor, String reason) {
        repository.save(new AuditEntry(action, pageId, requestId, postId, actor.id(), actor.displayName(), reason));
    }
}

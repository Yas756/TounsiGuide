package com.tounsiguide.businessservice.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Page<AuditEntry> findAllByPageIdAndActionIn(Long pageId, Collection<AuditAction> actions, Pageable pageable);

    List<AuditEntry> findAllByRequestIdOrderByIdAsc(Long requestId);
}

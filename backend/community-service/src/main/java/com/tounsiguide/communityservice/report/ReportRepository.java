package com.tounsiguide.communityservice.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterIdAndStatus(ReportTargetType targetType, Long targetId,
                                                                String reporterId, ReportStatus status);

    List<Report> findAllByStatusOrderByCreatedAtAsc(ReportStatus status);

    List<Report> findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);
}

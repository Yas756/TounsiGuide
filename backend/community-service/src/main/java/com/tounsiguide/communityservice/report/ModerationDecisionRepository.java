package com.tounsiguide.communityservice.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationDecisionRepository extends JpaRepository<ModerationDecision, Long> {

    List<ModerationDecision> findAllByTargetTypeAndTargetIdOrderByIdDesc(ReportTargetType targetType, Long targetId);
}

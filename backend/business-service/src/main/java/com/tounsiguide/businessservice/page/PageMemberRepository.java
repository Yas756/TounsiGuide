package com.tounsiguide.businessservice.page;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageMemberRepository extends JpaRepository<PageMember, Long> {

    Optional<PageMember> findByPageIdAndUserId(Long pageId, String userId);

    List<PageMember> findAllByPageIdOrderByAddedAtAsc(Long pageId);
}

package com.tounsiguide.communityservice.topic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicModuleRepository extends JpaRepository<TopicModule, Long> {

    List<TopicModule> findAllByStatusOrderByNameAsc(ModuleStatus status);

    Page<TopicModule> findAllByStatus(ModuleStatus status, Pageable pageable);

    List<TopicModule> findAllByProposedByOrderByCreatedAtDesc(String proposedBy);

    Optional<TopicModule> findFirstBySlugAndStatus(String slug, ModuleStatus status);

    Optional<TopicModule> findFirstBySlugOrderByIdDesc(String slug);

    boolean existsBySlugAndStatusNot(String slug, ModuleStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, ModuleStatus status);

    List<TopicModule> findAllByIdIn(Collection<Long> ids);
}

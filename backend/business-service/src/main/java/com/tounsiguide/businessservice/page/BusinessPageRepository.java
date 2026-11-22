package com.tounsiguide.businessservice.page;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessPageRepository extends JpaRepository<BusinessPage, Long> {

    boolean existsByLocationIdAndStatus(Long locationId, PageStatus status);

    Optional<BusinessPage> findByLocationIdAndStatus(Long locationId, PageStatus status);

    Optional<BusinessPage> findByIdAndStatus(Long id, PageStatus status);

    Page<BusinessPage> findAllByStatus(PageStatus status, Pageable pageable);

    Page<BusinessPage> findAllByStatusAndNameContainingIgnoreCase(PageStatus status, String name, Pageable pageable);
}

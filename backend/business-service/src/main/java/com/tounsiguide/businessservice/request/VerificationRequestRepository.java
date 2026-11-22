package com.tounsiguide.businessservice.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    boolean existsByLocationIdAndRequesterIdAndStatus(Long locationId, String requesterId, RequestStatus status);

    Page<VerificationRequest> findAllByRequesterId(String requesterId, Pageable pageable);

    Page<VerificationRequest> findAllByStatus(RequestStatus status, Pageable pageable);
}

package com.tounsiguide.businessservice.wall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WallVisibilityOverrideRepository extends JpaRepository<WallVisibilityOverride, Long> {

    Optional<WallVisibilityOverride> findByPageIdAndPostId(Long pageId, Long postId);

    List<WallVisibilityOverride> findAllByPageIdAndHiddenTrue(Long pageId);
}

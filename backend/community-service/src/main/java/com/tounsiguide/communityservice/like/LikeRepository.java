package com.tounsiguide.communityservice.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByTargetTypeAndTargetIdAndUserId(LikeTargetType targetType, Long targetId, String userId);

    long countByTargetTypeAndTargetId(LikeTargetType targetType, Long targetId);

    @Modifying
    @Query("delete from LikeEntry l where l.targetType = :type and l.targetId = :id and l.userId = :userId")
    int deleteOne(@Param("type") LikeTargetType type, @Param("id") Long id, @Param("userId") String userId);

    /** Rows of [targetId, count] for the given targets (targets without likes are absent). */
    @Query("select l.targetId, count(l) from LikeEntry l where l.targetType = :type and l.targetId in :ids group by l.targetId")
    List<Object[]> countByTargets(@Param("type") LikeTargetType type, @Param("ids") Collection<Long> ids);

    @Query("select l.targetId from LikeEntry l where l.targetType = :type and l.targetId in :ids and l.userId = :userId")
    List<Long> likedByUser(@Param("type") LikeTargetType type, @Param("ids") Collection<Long> ids, @Param("userId") String userId);
}

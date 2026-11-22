package com.tounsiguide.locationservice.association;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssociationRepository extends JpaRepository<Association, Long> {

    Optional<Association> findByPostIdAndLocationId(Long postId, Long locationId);

    @EntityGraph(attributePaths = {"location", "evidence"})
    List<Association> findByPostId(Long postId);

    List<Association> findByPostIdIn(Collection<Long> postIds);

    boolean existsByPostIdAndLocationIdAndAuthorClaimTrue(Long postId, Long locationId);

    /**
     * Review queue: DISPUTED first, then the lowest score, oldest first. Associations of deleted posts stay in the
     * queue (flagged {@code postDeleted}, so the admin can close them) but come after those of existing posts.
     */
    @Query(value = """
            select a from Association a
            where a.status in :statuses
            order by a.postDeleted asc,
                     case when a.status = com.tounsiguide.locationservice.association.Status.DISPUTED then 0 else 1 end,
                     a.score asc, a.id asc
            """,
            countQuery = """
            select count(a) from Association a
            where a.status in :statuses
            """)
    Page<Association> findQueue(@Param("statuses") Collection<Status> statuses, Pageable pageable);

    /** Posts confirmed for a Location, most recently changed first (at most one CONFIRMED per post). */
    @Query(value = """
            select a.postId from Association a
            where a.location.id = :locationId
              and a.status = com.tounsiguide.locationservice.association.Status.CONFIRMED
              and a.postDeleted = false
            order by a.updatedAt desc, a.id desc
            """,
            countQuery = """
            select count(a) from Association a
            where a.location.id = :locationId
              and a.status = com.tounsiguide.locationservice.association.Status.CONFIRMED
              and a.postDeleted = false
            """)
    Page<Long> findConfirmedPostIds(@Param("locationId") Long locationId, Pageable pageable);

    /** History signal: past decided associations of the same author or of the same Locations, on other posts. */
    @Query("""
            select a from Association a
            where a.postId <> :postId
              and a.status in :statuses
              and (a.postAuthorId = :authorId or a.location.id in :locationIds)
            order by a.id desc
            """)
    List<Association> findPrevious(@Param("postId") Long postId,
                                   @Param("authorId") String authorId,
                                   @Param("locationIds") Collection<Long> locationIds,
                                   @Param("statuses") Collection<Status> statuses,
                                   Pageable pageable);
}

package com.tounsiguide.businessservice.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PageRatingRepository extends JpaRepository<PageRating, Long> {

    Optional<PageRating> findByPageIdAndUserId(Long pageId, String userId);

    /** Rows of [stars, count] for one page. */
    @Query("select r.stars, count(r) from PageRating r where r.pageId = :pageId group by r.stars")
    List<Object[]> distribution(@Param("pageId") Long pageId);

    /** Rows of [pageId, average, count] for several pages (pages without ratings are absent). */
    @Query("select r.pageId, avg(r.stars), count(r) from PageRating r where r.pageId in :pageIds group by r.pageId")
    List<Object[]> averages(@Param("pageIds") Collection<Long> pageIds);
}

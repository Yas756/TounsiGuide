package com.tounsiguide.communityservice.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** One page of the feed: deleted posts stay in the table (soft delete) but are left out here. */
    Page<Post> findAllByPublicationStatus(PublicationStatus publicationStatus, Pageable pageable);

    /** Several posts by id, with a given status (used by GET /api/v1/posts/batch). */
    /** One page of the feed limited to a topic module (GET /api/v1/posts?module=slug, S3-B08). */
    Page<Post> findAllByPublicationStatusAndModuleId(PublicationStatus publicationStatus, Long moduleId, Pageable pageable);

    /**
     * Unified search (S3-US10): posts whose text or author name contains {@code pattern} (a LIKE pattern
     * with {@code !} as escape character). The utf8mb4_unicode_ci columns make it case and accent insensitive.
     */
    @Query("""
            select p from Post p
            where p.publicationStatus = :status
              and (:moduleId is null or p.moduleId = :moduleId)
              and (lower(p.content) like :pattern escape '!'
                   or lower(p.authorName) like :pattern escape '!')
            """)
    Page<Post> search(@Param("status") PublicationStatus status, @Param("moduleId") Long moduleId,
                      @Param("pattern") String pattern, Pageable pageable);

    /** [moduleId, number of posts] for every module that has posts with this status. */
    @Query("select p.moduleId, count(p) from Post p where p.publicationStatus = :status and p.moduleId is not null group by p.moduleId")
    List<Object[]> countPublishedByModule(@Param("status") PublicationStatus status);

    /** Restricted posts for the moderators' "restore" list (S3-B07). */
    List<Post> findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus publicationStatus);

    List<Post> findAllByIdInAndPublicationStatus(Collection<Long> ids, PublicationStatus publicationStatus);
}

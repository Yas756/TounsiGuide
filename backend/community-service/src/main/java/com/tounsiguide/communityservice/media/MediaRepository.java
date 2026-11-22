package com.tounsiguide.communityservice.media;

import com.tounsiguide.communityservice.post.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MediaRepository extends JpaRepository<Media, String> {

    List<Media> findAllByPostIdOrderByPositionAsc(Long postId);

    List<Media> findAllByCommentIdOrderByPositionAsc(Long commentId);

    /** One query for a whole page of posts (no N+1). */
    List<Media> findAllByPostIdInOrderByPositionAsc(Collection<Long> postIds);

    List<Media> findAllByCommentIdInOrderByPositionAsc(Collection<Long> commentIds);

    /** True when the media belongs to a post with this status, or to a comment of such a post. */
    @Query("select count(m) > 0 from Media m where m.id = :mediaId and ("
            + "m.postId in (select p.id from Post p where p.publicationStatus = :status)"
            + " or m.commentId in (select c.id from Comment c where c.post.publicationStatus = :status))")
    boolean existsByIdAndPostStatus(@Param("mediaId") String mediaId, @Param("status") PublicationStatus status);

    List<Media> findAllByPostIdIsNullAndCommentIdIsNullAndCreatedAtBefore(Instant before);
}

package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.post.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findAllByPostIdOrderByCreatedAtAsc(Long postId);

    /** Restricted comments for the moderators' "restore" list (S3-B07). */
    List<Comment> findAllByPublicationStatusOrderByRestrictedAtDesc(PublicationStatus publicationStatus);
}

package com.tounsiguide.communityservice.comment.dto;

import com.tounsiguide.communityservice.media.dto.MediaResponse;

import com.tounsiguide.communityservice.post.PublicationStatus;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id,
        Long postId,
        String content,
        String authorId,
        String authorName,
        Instant createdAt,
        Instant updatedAt,
        List<MediaResponse> media,
        /** PUBLISHED, or RESTRICTED (moderation): only administrators and the comment's author receive those. */
        PublicationStatus publicationStatus
) {

    /** A published comment (keeps older call sites and tests short). */
    public CommentResponse(Long id, Long postId, String content, String authorId, String authorName, Instant createdAt,
                           Instant updatedAt, List<MediaResponse> media) {
        this(id, postId, content, authorId, authorName, createdAt, updatedAt, media, PublicationStatus.PUBLISHED);
    }
}

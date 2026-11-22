package com.tounsiguide.businessservice.wall.dto;

import com.tounsiguide.businessservice.client.CommunityClient.PostSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A post on a wall: the Community Service's post data as it is now, plus the wall's own visibility.
 * {@code hidden}, {@code hiddenReason} and {@code hiddenAt} are only ever true/filled for page members.
 */
public record WallPostResponse(
        Long id,
        String content,
        String authorId,
        String authorName,
        Long locationId,
        Instant createdAt,
        Instant updatedAt,
        List<Map<String, Object>> media,
        boolean hidden,
        String hiddenReason,
        Instant hiddenAt
) {

    public static WallPostResponse of(PostSnapshot post, boolean hidden, String hiddenReason, Instant hiddenAt) {
        return new WallPostResponse(post.id(), post.content(), post.authorId(), post.authorName(), post.locationId(),
                post.createdAt(), post.updatedAt(), post.media() == null ? List.of() : post.media(), hidden,
                hiddenReason, hiddenAt);
    }
}

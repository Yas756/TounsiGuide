package com.tounsiguide.businessservice.wall.dto;

import com.tounsiguide.businessservice.wall.WallVisibilityOverride;

import java.time.Instant;

/** Answer of hide/unhide: the post's visibility on this wall now. */
public record WallVisibilityResponse(
        Long pageId,
        Long postId,
        boolean hidden,
        String reason,
        String actorId,
        String actorName,
        Instant changedAt
) {

    public static WallVisibilityResponse from(WallVisibilityOverride override) {
        return new WallVisibilityResponse(override.getPageId(), override.getPostId(), override.isHidden(),
                override.getReason(), override.getActorId(), override.getActorName(), override.getChangedAt());
    }
}

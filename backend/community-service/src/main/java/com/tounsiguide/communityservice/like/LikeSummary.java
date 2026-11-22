package com.tounsiguide.communityservice.like;

/**
 * Likes of one post or comment: how many, and whether the caller is one of them ({@code likedByMe} is false for
 * a visitor).
 */
public record LikeSummary(Long targetId, long count, boolean likedByMe) {
}

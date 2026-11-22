package com.tounsiguide.communityservice.like;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Likes (S3-US11). Reading is public (the Gateway forwards the caller's identity when there is a token, for
 * {@code likedByMe}); liking needs a login. PUT likes, DELETE removes the like; both answer the new summary.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /** {@code GET /api/v1/posts/likes?ids=3,1,2}: likes of several posts (for a feed page). */
    @GetMapping("/likes")
    public List<LikeSummary> postLikes(
            @RequestParam List<Long> ids,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId
    ) {
        return likeService.postSummaries(ids, userId);
    }

    @PutMapping("/{postId}/like")
    public LikeSummary likePost(@PathVariable Long postId, CurrentUser user) {
        return likeService.likePost(postId, user);
    }

    @DeleteMapping("/{postId}/like")
    public LikeSummary unlikePost(@PathVariable Long postId, CurrentUser user) {
        return likeService.unlikePost(postId, user);
    }

    /** Likes of every visible comment of a post. */
    @GetMapping("/{postId}/comments/likes")
    public List<LikeSummary> commentLikes(
            @PathVariable Long postId,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId
    ) {
        return likeService.commentSummaries(postId, CurrentUser.isAdminRoles(roles), userId);
    }

    @PutMapping("/{postId}/comments/{commentId}/like")
    public LikeSummary likeComment(@PathVariable Long postId, @PathVariable Long commentId, CurrentUser user) {
        return likeService.likeComment(postId, commentId, user);
    }

    @DeleteMapping("/{postId}/comments/{commentId}/like")
    public LikeSummary unlikeComment(@PathVariable Long postId, @PathVariable Long commentId, CurrentUser user) {
        return likeService.unlikeComment(postId, commentId, user);
    }
}

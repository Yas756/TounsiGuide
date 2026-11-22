package com.tounsiguide.communityservice.like;

import com.tounsiguide.communityservice.comment.Comment;
import com.tounsiguide.communityservice.comment.CommentService;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.post.PostService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Likes on posts and comments (S3-US11). Liking twice or unliking something not liked changes nothing
 * (idempotent). Only visible content can be liked: a deleted or restricted post or comment answers 404.
 */
@Service
public class LikeService {

    /** Most targets asked at once in a summary. */
    public static final int MAX_IDS = 100;

    private final LikeRepository likeRepository;
    private final PostService postService;
    private final CommentService commentService;

    public LikeService(LikeRepository likeRepository, PostService postService, CommentService commentService) {
        this.likeRepository = likeRepository;
        this.postService = postService;
        this.commentService = commentService;
    }

    @Transactional
    public LikeSummary likePost(Long postId, CurrentUser user) {
        postService.getPostById(postId);
        return like(LikeTargetType.POST, postId, user);
    }

    @Transactional
    public LikeSummary unlikePost(Long postId, CurrentUser user) {
        postService.getPostById(postId);
        return unlike(LikeTargetType.POST, postId, user);
    }

    @Transactional
    public LikeSummary likeComment(Long postId, Long commentId, CurrentUser user) {
        commentService.getCommentById(postId, commentId);
        return like(LikeTargetType.COMMENT, commentId, user);
    }

    @Transactional
    public LikeSummary unlikeComment(Long postId, Long commentId, CurrentUser user) {
        commentService.getCommentById(postId, commentId);
        return unlike(LikeTargetType.COMMENT, commentId, user);
    }

    /** Likes of several posts at once (1 to 100 ids), in the order asked. Unknown ids simply have 0 likes. */
    @Transactional(readOnly = true)
    public List<LikeSummary> postSummaries(List<Long> ids, String viewerId) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_IDS) {
            throw new InvalidParameterException("ids", "ids must contain 1 to " + MAX_IDS + " post ids");
        }
        return summaries(LikeTargetType.POST, ids, viewerId);
    }

    /** Likes of every comment of a post that this reader can see. */
    @Transactional(readOnly = true)
    public List<LikeSummary> commentSummaries(Long postId, boolean admin, String viewerId) {
        List<Long> ids = commentService.getAllCommentsForPost(postId, admin, viewerId).stream().map(Comment::getId).toList();
        return ids.isEmpty() ? List.of() : summaries(LikeTargetType.COMMENT, ids, viewerId);
    }

    private LikeSummary like(LikeTargetType type, Long id, CurrentUser user) {
        if (!likeRepository.existsByTargetTypeAndTargetIdAndUserId(type, id, user.id())) {
            try {
                likeRepository.saveAndFlush(new Like(type, id, user.id()));
            } catch (DataIntegrityViolationException alreadyLiked) {
                // A double click raced us: the like exists, which is what was asked.
            }
        }
        return new LikeSummary(id, likeRepository.countByTargetTypeAndTargetId(type, id), true);
    }

    private LikeSummary unlike(LikeTargetType type, Long id, CurrentUser user) {
        likeRepository.deleteOne(type, id, user.id());
        return new LikeSummary(id, likeRepository.countByTargetTypeAndTargetId(type, id), false);
    }

    private List<LikeSummary> summaries(LikeTargetType type, List<Long> ids, String viewerId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : likeRepository.countByTargets(type, ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Set<Long> mine = viewerId == null ? Set.of() : new HashSet<>(likeRepository.likedByUser(type, ids, viewerId));
        return ids.stream().distinct().map(id -> new LikeSummary(id, counts.getOrDefault(id, 0L), mine.contains(id))).toList();
    }
}

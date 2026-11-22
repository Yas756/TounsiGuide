package com.tounsiguide.communityservice.like;

import com.tounsiguide.communityservice.comment.CommentService;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.post.PostService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final CurrentUser SARRA = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));

    @Mock
    private LikeRepository likes;
    @Mock
    private PostService postService;
    @Mock
    private CommentService commentService;
    @InjectMocks
    private LikeService service;

    @Test
    void likingAPostStoresOneLikeAndAnswersTheNewCount() {
        when(likes.existsByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, 7L, "user-1")).thenReturn(false);
        when(likes.countByTargetTypeAndTargetId(LikeTargetType.POST, 7L)).thenReturn(3L);

        LikeSummary summary = service.likePost(7L, SARRA);

        verify(postService).getPostById(7L);
        verify(likes).saveAndFlush(any(Like.class));
        assertThat(summary).isEqualTo(new LikeSummary(7L, 3L, true));
    }

    @Test
    void likingTwiceDoesNotAddASecondLike() {
        when(likes.existsByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, 7L, "user-1")).thenReturn(true);
        when(likes.countByTargetTypeAndTargetId(LikeTargetType.POST, 7L)).thenReturn(1L);

        assertThat(service.likePost(7L, SARRA).count()).isEqualTo(1L);
        verify(likes, never()).saveAndFlush(any());
    }

    @Test
    void aDoubleClickRaceStillAnswersLiked() {
        when(likes.existsByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, 7L, "user-1")).thenReturn(false);
        when(likes.saveAndFlush(any(Like.class))).thenThrow(new DataIntegrityViolationException("uk_likes_target_user"));
        when(likes.countByTargetTypeAndTargetId(LikeTargetType.POST, 7L)).thenReturn(1L);

        assertThat(service.likePost(7L, SARRA)).isEqualTo(new LikeSummary(7L, 1L, true));
    }

    @Test
    void aDeletedOrRestrictedPostCannotBeLiked() {
        when(postService.getPostById(9L)).thenThrow(new EntityNotFoundException("Post not found: 9"));

        assertThatThrownBy(() -> service.likePost(9L, SARRA)).isInstanceOf(EntityNotFoundException.class);
        verify(likes, never()).saveAndFlush(any());
    }

    @Test
    void unlikingRemovesTheCallersLikeOnly() {
        when(likes.countByTargetTypeAndTargetId(LikeTargetType.COMMENT, 4L)).thenReturn(0L);

        LikeSummary summary = service.unlikeComment(2L, 4L, SARRA);

        verify(commentService).getCommentById(2L, 4L);
        verify(likes).deleteOne(LikeTargetType.COMMENT, 4L, "user-1");
        assertThat(summary).isEqualTo(new LikeSummary(4L, 0L, false));
    }

    @Test
    void summariesGiveZeroForPostsWithoutLikesAndFlagTheCallersOwn() {
        when(likes.countByTargets(eq(LikeTargetType.POST), anyList())).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(likes.likedByUser(eq(LikeTargetType.POST), anyList(), eq("user-1"))).thenReturn(List.of(1L));

        assertThat(service.postSummaries(List.of(1L, 2L), "user-1"))
                .containsExactly(new LikeSummary(1L, 5L, true), new LikeSummary(2L, 0L, false));
    }

    @Test
    void aVisitorIsNeverLikedByMe() {
        when(likes.countByTargets(eq(LikeTargetType.POST), anyList())).thenReturn(List.<Object[]>of(new Object[]{1L, 2L}));

        assertThat(service.postSummaries(List.of(1L), null)).containsExactly(new LikeSummary(1L, 2L, false));
        verify(likes, never()).likedByUser(any(), anyList(), any());
    }

    @Test
    void summariesNeedOneToHundredIds() {
        assertThatThrownBy(() -> service.postSummaries(List.of(), null)).isInstanceOf(InvalidParameterException.class);
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service.postSummaries(tooMany, null)).isInstanceOf(InvalidParameterException.class);
    }
}

package com.tounsiguide.communityservice.report;

import com.tounsiguide.communityservice.comment.Comment;
import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.comment.CommentService;
import com.tounsiguide.communityservice.location.LocationClient;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PostService;
import com.tounsiguide.communityservice.post.PublicationStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/** Who still sees a RESTRICTED post or comment (S3-B07): administrators and the author, nobody else. */
@ExtendWith(MockitoExtension.class)
class RestrictedVisibilityTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private LocationClient locationClient;
    @Mock
    private PostChangeNotifier changeNotifier;
    @Mock
    private MediaService mediaService;

    private PostService postService;
    private CommentService commentService;
    private Post post;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, locationClient, changeNotifier, mediaService, null);
        commentService = new CommentService(commentRepository, postRepository, changeNotifier, mediaService);
        post = new Post("Bon café", "author-1", "Sarra", 14L);
        setField(post, "id", 12L);
        lenient().when(postRepository.findById(12L)).thenReturn(Optional.of(post));
    }

    @Test
    void aRestrictedPostIs404ForThePublicAndOtherMembersButVisibleToItsAuthorAndAdmins() {
        post.markRestricted("admin-1", "Publicité");

        assertThatThrownBy(() -> postService.getPostById(12L, false, null)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> postService.getPostById(12L, false, "user-2")).isInstanceOf(EntityNotFoundException.class);
        assertThat(postService.getPostById(12L, false, "author-1").getPublicationStatus()).isEqualTo(PublicationStatus.RESTRICTED);
        assertThat(postService.getPostById(12L, true, null).getPublicationStatus()).isEqualTo(PublicationStatus.RESTRICTED);
    }

    @Test
    void theFeedAndTheBatchAskForPublishedPostsOnlySoWallsDropRestrictedOnes() {
        postService.getPublishedPostsByIds(List.of(12L));

        verify(postRepository).findAllByIdInAndPublicationStatus(List.of(12L), PublicationStatus.PUBLISHED);
    }

    @Test
    void aRestrictedPostCannotBeEditedOrCommented() {
        post.markRestricted("admin-1", "Publicité");
        var author = new com.tounsiguide.communityservice.common.security.CurrentUser("author-1", "Sarra", java.util.Set.of("MEMBER"));

        assertThatThrownBy(() -> postService.updatePost(12L, "x", 14L, author)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> commentService.createComment(12L, "x", author)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void restrictedCommentsAreLeftOutExceptForTheirAuthorAndAdmins() {
        Comment visible = new Comment(post, "Merci", "user-2", "Ali");
        Comment restricted = new Comment(post, "Insulte", "user-3", "Karim");
        restricted.markRestricted("admin-1", "Insultes");
        lenient().when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(12L)).thenReturn(List.of(visible, restricted));

        assertThat(commentService.getAllCommentsForPost(12L, false, null)).containsExactly(visible);
        assertThat(commentService.getAllCommentsForPost(12L, false, "user-3")).containsExactly(visible, restricted);
        assertThat(commentService.getAllCommentsForPost(12L, true, null)).containsExactly(visible, restricted);
    }

    @Test
    void theCommentsOfARestrictedPostAre404ExceptForItsAuthor() {
        post.markRestricted("admin-1", "Publicité");
        lenient().when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(12L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.getAllCommentsForPost(12L, false, "user-2"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(commentService.getAllCommentsForPost(12L, false, "author-1")).isEmpty();
    }
}

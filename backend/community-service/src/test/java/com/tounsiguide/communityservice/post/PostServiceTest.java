package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.location.DummyLocationService;
import com.tounsiguide.communityservice.post.dto.PlaceDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final CurrentUser AUTHOR = new CurrentUser("user-1", "Sarra", java.util.Set.of("MEMBER"));
    private static final CurrentUser STRANGER = new CurrentUser("user-2", "Ali", java.util.Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", java.util.Set.of("MEMBER", "ADMIN"));

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private DummyLocationService locationService;

    @InjectMocks
    private PostService postService;

    private Post postBy(CurrentUser user, String content) {
        return new Post(content, user.id(), user.displayName(), null);
    }

    @Test
    void createPostUsesTheLoggedInUserAsAuthor() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Post createdPost = postService.createPost("A local recommendation", 7L, null, AUTHOR);

        assertThat(createdPost.getContent()).isEqualTo("A local recommendation");
        assertThat(createdPost.getAuthorId()).isEqualTo("user-1");
        assertThat(createdPost.getAuthorName()).isEqualTo("Sarra");
        assertThat(createdPost.getLocationId()).isEqualTo(7L);
        verify(postRepository).save(createdPost);
    }

    @Test
    void createPostStoresThePlaceChosenOnTheMap() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PlaceDto place = new PlaceDto("Café des Nattes", "Sidi Bou Said, Tunisie",
                36.8702, 10.3416, null);

        Post createdPost = postService.createPost("Un café magnifique", null, place, AUTHOR);

        assertThat(createdPost.getPlaceName()).isEqualTo("Café des Nattes");
        assertThat(createdPost.getPlaceAddress()).isEqualTo("Sidi Bou Said, Tunisie");
        assertThat(createdPost.getLatitude()).isEqualTo(36.8702);
        assertThat(createdPost.getLongitude()).isEqualTo(10.3416);
    }

    @Test
    void getAllPostsReturnsRepositoryResults() {
        Post firstPost = postBy(AUTHOR, "First post");
        Post secondPost = postBy(STRANGER, "Second post");
        when(postRepository.findAll()).thenReturn(List.of(firstPost, secondPost));

        List<Post> posts = postService.getAllPosts();

        assertThat(posts).containsExactly(firstPost, secondPost);
        verify(postRepository).findAll();
    }

    @Test
    void theAuthorCanUpdateContentLocationAndPlace() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        Post updatedPost = postService.updatePost(1L, "Updated content", 3L, null, AUTHOR);

        assertThat(updatedPost.getContent()).isEqualTo("Updated content");
        assertThat(updatedPost.getLocationId()).isEqualTo(3L);
        assertThat(updatedPost.getAuthorId()).isEqualTo("user-1");
        verify(postRepository).save(existingPost);
    }

    @Test
    void updatingWithoutAPlaceRemovesThePlace() {
        Post existingPost = postBy(AUTHOR, "Original content");
        existingPost.setPlace("Old place", null, 36.8, 10.1, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        Post updatedPost = postService.updatePost(1L, "Updated content", null, null, AUTHOR);

        assertThat(updatedPost.getPlaceName()).isNull();
        assertThat(updatedPost.getLatitude()).isNull();
        assertThat(updatedPost.getLongitude()).isNull();
    }

    @Test
    void aStrangerCannotUpdateAPost() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.updatePost(1L, "Hacked", null, null, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        assertThat(existingPost.getContent()).isEqualTo("Original content");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void anAdminCannotEditAPostButCanDeleteIt() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.updatePost(1L, "Edited", null, null, ADMIN))
                .isInstanceOf(ForbiddenException.class);

        postService.deletePost(1L, ADMIN);

        verify(postRepository).delete(existingPost);
    }

    @Test
    void theAuthorCanDeleteThePostAndItsComments() {
        Post existingPost = postBy(AUTHOR, "Post with comments");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        postService.deletePost(1L, AUTHOR);

        var ordered = inOrder(commentRepository, postRepository);
        ordered.verify(commentRepository).deleteAllByPostId(1L);
        ordered.verify(postRepository).delete(existingPost);
    }

    @Test
    void aStrangerCannotDeleteAPost() {
        Post existingPost = postBy(AUTHOR, "Post to keep");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.deletePost(1L, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).delete(any(Post.class));
        verify(commentRepository, never()).deleteAllByPostId(any());
    }

    @Test
    void missingPostCausesEntityNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }
}

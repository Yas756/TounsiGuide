package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.location.DummyLocationService;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private DummyLocationService locationService;

    @InjectMocks
    private PostService postService;

    @Test
    void createPostUsesTheTemporaryDemoAuthor() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Post createdPost = postService.createPost("A local recommendation", 7L);

        assertThat(createdPost.getContent()).isEqualTo("A local recommendation");
        assertThat(createdPost.getAuthorId()).isEqualTo(PostService.DEMO_AUTHOR_ID);
        assertThat(createdPost.getAuthorName()).isEqualTo(PostService.DEMO_AUTHOR_NAME);
        assertThat(createdPost.getLocationId()).isEqualTo(7L);
        verify(postRepository).save(createdPost);
    }

    @Test
    void getAllPostsReturnsRepositoryResults() {
        Post firstPost = new Post("First post", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null);
        Post secondPost = new Post("Second post", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, 2L);
        when(postRepository.findAll()).thenReturn(List.of(firstPost, secondPost));

        List<Post> posts = postService.getAllPosts();

        assertThat(posts).containsExactly(firstPost, secondPost);
        verify(postRepository).findAll();
    }

    @Test
    void updatePostChangesContentAndLocation() {
        Post existingPost = new Post("Original content", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        Post updatedPost = postService.updatePost(1L, "Updated content", 3L);

        assertThat(updatedPost.getContent()).isEqualTo("Updated content");
        assertThat(updatedPost.getLocationId()).isEqualTo(3L);
        assertThat(updatedPost.getAuthorId()).isEqualTo(PostService.DEMO_AUTHOR_ID);
        verify(postRepository).save(existingPost);
    }

    @Test
    void deletePostDeletesTheExistingPost() {
        Post existingPost = new Post("Post to delete", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        postService.deletePost(1L);

        verify(postRepository).delete(existingPost);
        verify(commentRepository).deleteAllByPostId(1L);
    }

    @Test
    void deletePostDeletesCommentsBeforeDeletingThePost() {
        Post existingPost = new Post("Post with comments", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        postService.deletePost(1L);

        var ordered = inOrder(commentRepository, postRepository);
        ordered.verify(commentRepository).deleteAllByPostId(1L);
        ordered.verify(postRepository).delete(existingPost);
    }

    @Test
    void missingPostCausesEntityNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }
}

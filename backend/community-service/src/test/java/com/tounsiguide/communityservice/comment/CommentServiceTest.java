package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PostService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void createCommentUsesTheTemporaryDemoAuthorAndParentPost() {
        Post post = postWithId(1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Comment createdComment = commentService.createComment(1L, "Useful recommendation.");

        assertThat(createdComment.getPost()).isSameAs(post);
        assertThat(createdComment.getContent()).isEqualTo("Useful recommendation.");
        assertThat(createdComment.getAuthorId()).isEqualTo(PostService.DEMO_AUTHOR_ID);
        assertThat(createdComment.getAuthorName()).isEqualTo(PostService.DEMO_AUTHOR_NAME);
        verify(commentRepository).save(createdComment);
    }

    @Test
    void getAllCommentsRequiresAnExistingPost() {
        Post post = postWithId(1L);
        Comment first = new Comment(post, "First", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        Comment second = new Comment(post, "Second", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(first, second));

        List<Comment> comments = commentService.getAllCommentsForPost(1L);

        assertThat(comments).containsExactly(first, second);
        verify(commentRepository).findAllByPostIdOrderByCreatedAtAsc(1L);
    }

    @Test
    void updateCommentOnlyUpdatesCommentBelongingToTheRequestedPost() {
        Post post = postWithId(1L);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Original", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        Comment updatedComment = commentService.updateComment(1L, 10L, "Updated");

        assertThat(updatedComment.getContent()).isEqualTo("Updated");
        verify(commentRepository).save(comment);
    }

    @Test
    void updateCommentRejectsACommentBelongingToAnotherPost() {
        Post requestedPost = postWithId(1L);
        Post actualPost = postWithId(2L);
        when(actualPost.getId()).thenReturn(2L);
        Comment comment = new Comment(actualPost, "Original", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        when(postRepository.findById(1L)).thenReturn(Optional.of(requestedPost));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "Updated"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Comment not found: 10");
    }

    @Test
    void deleteCommentDeletesTheCommentBelongingToThePost() {
        Post post = postWithId(1L);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "To delete", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 10L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void createCommentRejectsAMissingPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(99L, "Invalid"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }

    private Post postWithId(Long id) {
        return mock(Post.class);
    }
}

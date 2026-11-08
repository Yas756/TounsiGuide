package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final CurrentUser AUTHOR = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));
    private static final CurrentUser STRANGER = new CurrentUser("user-2", "Ali", Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", Set.of("MEMBER", "ADMIN"));

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void createCommentUsesTheLoggedInUserAndTheParentPost() {
        Post post = mock(Post.class);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Comment createdComment = commentService.createComment(1L, "Useful recommendation.", AUTHOR);

        assertThat(createdComment.getPost()).isSameAs(post);
        assertThat(createdComment.getContent()).isEqualTo("Useful recommendation.");
        assertThat(createdComment.getAuthorId()).isEqualTo("user-1");
        assertThat(createdComment.getAuthorName()).isEqualTo("Sarra");
        verify(commentRepository).save(createdComment);
    }

    @Test
    void getAllCommentsRequiresAnExistingPost() {
        Post post = mock(Post.class);
        Comment first = new Comment(post, "First", "user-1", "Sarra");
        Comment second = new Comment(post, "Second", "user-2", "Ali");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(first, second));

        List<Comment> comments = commentService.getAllCommentsForPost(1L);

        assertThat(comments).containsExactly(first, second);
        verify(commentRepository).findAllByPostIdOrderByCreatedAtAsc(1L);
    }

    @Test
    void theAuthorCanUpdateTheirCommentOfTheRequestedPost() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Original", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        Comment updatedComment = commentService.updateComment(1L, 10L, "Updated", AUTHOR);

        assertThat(updatedComment.getContent()).isEqualTo("Updated");
        verify(commentRepository).save(comment);
    }

    @Test
    void aStrangerCannotUpdateAComment() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Original", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "Hacked", STRANGER))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void updateCommentRejectsACommentBelongingToAnotherPost() {
        Post requestedPost = mock(Post.class);
        Post actualPost = mock(Post.class);
        when(actualPost.getId()).thenReturn(2L);
        Comment comment = new Comment(actualPost, "Original", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(requestedPost));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "Updated", AUTHOR))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Comment not found: 10");
    }

    @Test
    void theAuthorCanDeleteTheirComment() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "To delete", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 10L, AUTHOR);

        verify(commentRepository).delete(comment);
    }

    @Test
    void anAdminCanDeleteAnyComment() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Offensive", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 10L, ADMIN);

        verify(commentRepository).delete(comment);
    }

    @Test
    void aStrangerCannotDeleteAComment() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Keep me", "user-1", "Sarra");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void createCommentRejectsAMissingPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(99L, "Invalid", AUTHOR))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }
}

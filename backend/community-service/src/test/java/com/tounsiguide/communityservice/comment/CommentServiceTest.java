package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private PostChangeNotifier changeNotifier;

    @Mock
    private MediaService mediaService;

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
        verify(changeNotifier).postChanged(1L);
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
        verify(changeNotifier).postChanged(1L);
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
        verifyNoInteractions(changeNotifier);
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
        verify(changeNotifier).postChanged(1L);
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
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void createCommentRejectsAMissingPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(99L, "Invalid", AUTHOR))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }

    // ---------------------------------------------------------------- soft-deleted post (S2-B04)

    private Post deletedPost() {
        Post post = mock(Post.class);
        org.mockito.Mockito.lenient().when(post.isDeleted()).thenReturn(true);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void theCommentsOfADeletedPostAnswer404ToThePublic() {
        deletedPost();

        assertThatThrownBy(() -> commentService.getAllCommentsForPost(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 1");
        assertThatThrownBy(() -> commentService.getCommentById(1L, 10L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(commentRepository, never()).findAllByPostIdOrderByCreatedAtAsc(any());
    }

    @Test
    void anAdminCanStillListTheCommentsOfADeletedPost() {
        Post post = deletedPost();
        Comment kept = new Comment(post, "Kept", "user-2", "Ali");
        when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(kept));

        assertThat(commentService.getAllCommentsForPost(1L, true)).containsExactly(kept);
    }

    @Test
    void nobodyCanCommentOrEditACommentOnADeletedPost() {
        deletedPost();

        assertThatThrownBy(() -> commentService.createComment(1L, "Late", AUTHOR))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "Edited", AUTHOR))
                .isInstanceOf(EntityNotFoundException.class);
        verify(commentRepository, never()).save(any(Comment.class));
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void theAuthorCanStillDeleteTheirCommentOfADeletedPost() {
        Post post = deletedPost();
        when(post.getId()).thenReturn(1L);
        Comment comment = new Comment(post, "Mine", "user-1", "Sarra");
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 10L, AUTHOR);

        verify(commentRepository).delete(comment);
    }
}

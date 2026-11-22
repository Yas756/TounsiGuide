package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.location.LocationClient;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.location.UnknownLocationException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final CurrentUser AUTHOR = new CurrentUser("user-1", "Sarra", java.util.Set.of("MEMBER"));
    private static final CurrentUser STRANGER = new CurrentUser("user-2", "Ali", java.util.Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", java.util.Set.of("MEMBER", "ADMIN"));

    @Mock
    private PostRepository postRepository;

    @Mock
    private LocationClient locationClient;

    @Mock
    private PostChangeNotifier changeNotifier;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private PostService postService;

    private Post postBy(CurrentUser user, String content) {
        return new Post(content, user.id(), user.displayName(), null);
    }

    @Test
    void createPostUsesTheLoggedInUserAsAuthor() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Post createdPost = postService.createPost("A local recommendation", 7L, AUTHOR);

        assertThat(createdPost.getContent()).isEqualTo("A local recommendation");
        assertThat(createdPost.getAuthorId()).isEqualTo("user-1");
        assertThat(createdPost.getAuthorName()).isEqualTo("Sarra");
        assertThat(createdPost.getLocationId()).isEqualTo(7L);
        verify(locationClient).requireExists(7L);
        verify(postRepository).save(createdPost);
    }

    @Test
    void createPostIsRejectedWhenTheLocationDoesNotExist() {
        doThrow(new UnknownLocationException(404L)).when(locationClient).requireExists(404L);

        assertThatThrownBy(() -> postService.createPost("Somewhere", 404L, AUTHOR))
                .isInstanceOf(UnknownLocationException.class);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void theFeedReturnsTheRepositoryPage() {
        Post firstPost = postBy(AUTHOR, "First post");
        Post secondPost = postBy(STRANGER, "Second post");
        Page<Post> page = new PageImpl<>(List.of(firstPost, secondPost), PageRequest.of(0, 20), 2);
        when(postRepository.findAllByPublicationStatus(eq(PublicationStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(page);

        assertThat(postService.getPublishedPosts(0, 20)).isSameAs(page);
    }

    @Test
    void theFeedAsksForPublishedPostsNewestFirstWithTheIdAsTieBreak() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(postRepository.findAllByPublicationStatus(eq(PublicationStatus.PUBLISHED), pageable.capture()))
                .thenReturn(Page.empty());

        postService.getPublishedPosts(2, 10);

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        verify(postRepository, never()).findAll();
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void theFeedRejectsPagesAndSizesOutOfBounds() {
        assertThatThrownBy(() -> postService.getPublishedPosts(-1, 20))
                .isInstanceOf(InvalidParameterException.class)
                .satisfies(e -> assertThat(((InvalidParameterException) e).getParameter()).isEqualTo("page"));
        assertThatThrownBy(() -> postService.getPublishedPosts(0, 0))
                .isInstanceOf(InvalidParameterException.class)
                .satisfies(e -> assertThat(((InvalidParameterException) e).getParameter()).isEqualTo("size"));
        assertThatThrownBy(() -> postService.getPublishedPosts(0, PostService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(postRepository);
    }

    @Test
    void theMaximumSizeIsAccepted() {
        when(postRepository.findAllByPublicationStatus(eq(PublicationStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(postService.getPublishedPosts(0, PostService.MAX_PAGE_SIZE)).isEmpty();
    }

    @Test
    void aNewPostIsPublished() {
        Post post = postBy(AUTHOR, "Fresh");

        assertThat(post.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(post.isDeleted()).isFalse();
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getDeletedBy()).isNull();
    }

    @Test
    void theAuthorCanUpdateContentAndLocation() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        Post updatedPost = postService.updatePost(1L, "Updated content", 3L, AUTHOR);

        assertThat(updatedPost.getContent()).isEqualTo("Updated content");
        assertThat(updatedPost.getLocationId()).isEqualTo(3L);
        assertThat(updatedPost.getAuthorId()).isEqualTo("user-1");
        verify(postRepository).save(existingPost);
    }

    @Test
    void updatingWithTheSameLocationDoesNotCallTheLocationService() {
        Post existingPost = new Post("Original content", AUTHOR.id(), AUTHOR.displayName(), 3L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        postService.updatePost(1L, "Updated content", 3L, AUTHOR);

        verifyNoInteractions(locationClient);
    }

    @Test
    void updatingWithoutALocationRemovesIt() {
        Post existingPost = new Post("Original content", AUTHOR.id(), AUTHOR.displayName(), 3L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        Post updatedPost = postService.updatePost(1L, "Updated content", null, AUTHOR);

        assertThat(updatedPost.getLocationId()).isNull();
    }

    @Test
    void aStrangerCannotUpdateAPost() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.updatePost(1L, "Hacked", null, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        assertThat(existingPost.getContent()).isEqualTo("Original content");
        verify(postRepository, never()).save(any(Post.class));
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void anAdminCannotEditAPostButCanDeleteIt() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.updatePost(1L, "Edited", null, ADMIN))
                .isInstanceOf(ForbiddenException.class);

        postService.deletePost(1L, ADMIN);

        assertThat(existingPost.isDeleted()).isTrue();
        assertThat(existingPost.getDeletedBy()).isEqualTo("admin-1");
    }

    @Test
    void deletingMarksThePostDeletedAndKeepsTheRowItsCommentsAndItsMedia() {
        Post existingPost = postBy(AUTHOR, "Post with comments");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        postService.deletePost(1L, AUTHOR);

        assertThat(existingPost.getPublicationStatus()).isEqualTo(PublicationStatus.DELETED);
        assertThat(existingPost.getDeletedAt()).isNotNull();
        assertThat(existingPost.getDeletedBy()).isEqualTo("user-1");
        verify(postRepository).save(existingPost);
        verify(postRepository, never()).delete(any(Post.class));
        verifyNoInteractions(mediaService);
        verify(changeNotifier).postChanged(1L);
    }

    @Test
    void deletingAnAlreadyDeletedPostAnswers404() {
        Post deleted = postBy(AUTHOR, "Gone");
        deleted.markDeleted(AUTHOR.id());
        when(postRepository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> postService.deletePost(1L, AUTHOR))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 1");
        assertThatThrownBy(() -> postService.deletePost(1L, ADMIN))
                .isInstanceOf(EntityNotFoundException.class);
        verify(postRepository, never()).save(any(Post.class));
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void aDeletedPostIsNotFoundForThePublicButReadableForAnAdmin() {
        Post deleted = postBy(AUTHOR, "Gone");
        deleted.markDeleted(AUTHOR.id());
        when(postRepository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> postService.getPostById(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 1");
        assertThat(postService.getPostById(1L, true)).isSameAs(deleted);
    }

    @Test
    void aDeletedPostCannotBeEdited() {
        Post deleted = postBy(AUTHOR, "Gone");
        deleted.markDeleted(AUTHOR.id());
        when(postRepository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> postService.updatePost(1L, "Back", null, AUTHOR))
                .isInstanceOf(EntityNotFoundException.class);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void aStrangerCannotDeleteAPost() {
        Post existingPost = postBy(AUTHOR, "Post to keep");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));

        assertThatThrownBy(() -> postService.deletePost(1L, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).save(any(Post.class));
        assertThat(existingPost.isDeleted()).isFalse();
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void createUpdateAndDeleteTellTheLocationServiceThePostChanged() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
        Post created = postService.createPost("At the Bardo", null, AUTHOR);
        when(postRepository.findById(5L)).thenReturn(Optional.of(created));

        postService.updatePost(5L, "At the Bardo museum", null, AUTHOR);
        postService.deletePost(5L, AUTHOR);

        verify(changeNotifier, org.mockito.Mockito.times(3)).postChanged(5L);
    }

    @Test
    void createAttachesTheMediaAndStillNotifiesTheLocationService() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        postService.createPost("With photos", null, List.of("m-1", "m-2"), AUTHOR);

        verify(mediaService).attachToPost(5L, List.of("m-1", "m-2"), AUTHOR);
        verify(changeNotifier).postChanged(5L);
    }

    @Test
    void updatePassesTheMediaIdsAsTheyAreNullMeaningKeep() {
        Post existingPost = postBy(AUTHOR, "Original content");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(existingPost)).thenReturn(existingPost);

        postService.updatePost(1L, "Same media", null, AUTHOR);
        postService.updatePost(1L, "No media", null, List.of(), AUTHOR);

        verify(mediaService).replacePostMedia(1L, null, AUTHOR);
        verify(mediaService).replacePostMedia(1L, List.of(), AUTHOR);
        verify(changeNotifier, org.mockito.Mockito.times(2)).postChanged(1L);
    }

    @Test
    void missingPostCausesEntityNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Post not found: 99");
    }

    // ----- batch read for page walls (Sprint 3, S3-B04)

    @Test
    void batchReadReturnsPublishedPostsInTheOrderAskedAndAsksOnlyForPublishedOnes() {
        Post first = postBy(AUTHOR, "First");
        Post third = postBy(AUTHOR, "Third");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(third, "id", 3L);
        when(postRepository.findAllByIdInAndPublicationStatus(List.of(3L, 2L, 1L), PublicationStatus.PUBLISHED))
                .thenReturn(List.of(first, third));

        List<Post> posts = postService.getPublishedPostsByIds(List.of(3L, 2L, 1L, 3L));

        assertThat(posts).extracting(Post::getId).containsExactly(3L, 1L);
    }

    @Test
    void batchReadOfNoIdsDoesNotQuery() {
        assertThat(postService.getPublishedPostsByIds(List.of())).isEmpty();
        verifyNoInteractions(postRepository);
    }

    @Test
    void batchReadOfMoreThanFiftyIdsIsAValidationError() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();

        assertThatThrownBy(() -> postService.getPublishedPostsByIds(ids))
                .isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(postRepository);
    }

    // --- Unified search (S3-US10)

    @Test
    void searchLooksForTheTrimmedLowerCaseTextAnywhereInPublishedPosts() {
        when(postRepository.search(eq(PublicationStatus.PUBLISHED), eq(null), eq("%café 50!%!_!!%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        postService.searchPublishedPosts("  Café 50%_!  ", 0, 20, null);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).search(eq(PublicationStatus.PUBLISHED), eq(null), eq("%café 50!%!_!!%"), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(page.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void searchCanStayWithinOneModule() {
        when(postRepository.search(eq(PublicationStatus.PUBLISHED), eq(7L), eq("%plage%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        postService.searchPublishedPosts("plage", 1, 5, 7L);

        verify(postRepository).search(eq(PublicationStatus.PUBLISHED), eq(7L), eq("%plage%"), any(Pageable.class));
    }

    @Test
    void searchTextMustBeBetweenTwoAndHundredCharacters() {
        assertThatThrownBy(() -> postService.searchPublishedPosts(" a ", 0, 20, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> postService.searchPublishedPosts("x".repeat(101), 0, 20, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> postService.searchPublishedPosts("café", 0, 51, null))
                .isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(postRepository);
    }
}

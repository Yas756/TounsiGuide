package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.media.MediaKind;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.dto.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @Mock
    private PostMapper postMapper;

    @Mock
    private MediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(postService, postMapper, mediaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void theListIsAPageWithDefaultPageAndSize() throws Exception {
        when(postService.getPublishedPosts(0, 20, null)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void theListPassesPageAndSizeAndAnswersTheTotals() throws Exception {
        Post post = new Post("Third", "user-1", "Sarra", null);
        setField(post, "id", 3L);
        when(postService.getPublishedPosts(1, 2, null)).thenReturn(new PageImpl<>(List.of(post), PageRequest.of(1, 2), 3));

        mockMvcWithRealMapper().perform(get("/api/v1/posts").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void aSizeOutOfBoundsIsAValidationError() throws Exception {
        when(postService.getPublishedPosts(0, 500, null))
                .thenThrow(new InvalidParameterException("size", "size must be between 1 and 50"));

        mockMvc.perform(get("/api/v1/posts").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.size").value("size must be between 1 and 50"));
    }

    @Test
    void aNonNumericIdOrPageIsABadRequestInTheCommonFormat() throws Exception {
        mockMvc.perform(get("/api/v1/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: postId"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mockMvc.perform(get("/api/v1/posts").param("page", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getPostByIdReturnsOk() throws Exception {
        Post post = new Post("A post", "user-1",
                "Sarra", null);
        PostResponse response = new PostResponse(
                1L, "A post", "user-1",
                "Sarra", null,
                Instant.now(), Instant.now(), PublicationStatus.PUBLISHED, List.of()
        );
        when(postService.getPostById(1L, false, null)).thenReturn(post);
        when(postMapper.toResponse(post, List.of(), null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/posts/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private static final MediaResponse PHOTO = new MediaResponse(
            "m-1", MediaKind.IMAGE, MediaResponse.fileUrl("m-1"), "image/jpeg", 1234);
    private static final MediaResponse VIDEO = new MediaResponse(
            "m-2", MediaKind.VIDEO, MediaResponse.fileUrl("m-2"), "video/mp4", 5678);

    private MockMvc mockMvcWithRealMapper() {
        return MockMvcBuilders.standaloneSetup(new PostController(postService, new PostMapper(), mediaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPostByIdIncludesTheMediaInOrder() throws Exception {
        Post post = new Post("At the Bardo", "user-1", "Sarra", null);
        setField(post, "id", 1L);
        when(postService.getPostById(1L, false, null)).thenReturn(post);
        when(mediaService.mediaOfPost(1L)).thenReturn(List.of(PHOTO, VIDEO));

        mockMvcWithRealMapper().perform(get("/api/v1/posts/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].id").value("m-1"))
                .andExpect(jsonPath("$.media[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.media[0].url").value("/api/v1/media/m-1/file"))
                .andExpect(jsonPath("$.media[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.media[0].sizeBytes").value(1234))
                .andExpect(jsonPath("$.media[1].type").value("VIDEO"));
    }

    @Test
    void theListLoadsTheMediaOfAllPostsInOneCall() throws Exception {
        Post withMedia = new Post("First", "user-1", "Sarra", null);
        setField(withMedia, "id", 1L);
        Post withoutMedia = new Post("Second", "user-1", "Sarra", null);
        setField(withoutMedia, "id", 2L);
        when(postService.getPublishedPosts(0, 20, null)).thenReturn(new PageImpl<>(List.of(withMedia, withoutMedia)));
        when(mediaService.mediaOfPosts(List.of(1L, 2L))).thenReturn(Map.of(1L, List.of(PHOTO)));

        mockMvcWithRealMapper().perform(get("/api/v1/posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].media[0].id").value("m-1"))
                .andExpect(jsonPath("$.content[1].media.length()").value(0));
        org.mockito.Mockito.verify(mediaService, org.mockito.Mockito.never()).mediaOfPost(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aPublishedPostSaysSo() throws Exception {
        Post post = new Post("Visible", "user-1", "Sarra", null);
        setField(post, "id", 1L);
        when(postService.getPostById(1L, false, null)).thenReturn(post);

        mockMvcWithRealMapper().perform(get("/api/v1/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"));
    }

    @Test
    void aDeletedPostIs404ForThePublic() throws Exception {
        when(postService.getPostById(1L, false, null))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Post not found: 1"));

        mockMvcWithRealMapper().perform(get("/api/v1/posts/1").header("X-User-Roles", "MEMBER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void anAdminCanReadADeletedPost() throws Exception {
        Post post = new Post("Removed", "user-1", "Sarra", null);
        setField(post, "id", 1L);
        post.markDeleted("user-1");
        when(postService.getPostById(1L, true, null)).thenReturn(post);

        mockMvcWithRealMapper().perform(get("/api/v1/posts/1").header("X-User-Roles", "MEMBER,ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("DELETED"));
    }

    @Test
    void batchReturnsTheListOfPostsWithMedia() throws Exception {
        Post post = new Post("Bon café", "user-1", "Sarra", 14L);
        setField(post, "id", 3L);
        when(postService.getPublishedPostsByIds(List.of(3L, 9L))).thenReturn(List.of(post));
        when(mediaService.mediaOfPosts(List.of(3L))).thenReturn(Map.of());

        mockMvcWithRealMapper().perform(get("/api/v1/posts/batch").param("ids", "3,9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].locationId").value(14))
                .andExpect(jsonPath("$[0].media.length()").value(0));
    }

    @Test
    void batchWithoutIdsIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/posts/batch"))
                .andExpect(status().isBadRequest());
    }

    // --- Unified search (S3-US10)

    @Test
    void qSearchesThePostsInsteadOfListingTheFeed() throws Exception {
        Post post = new Post("Le meilleur lablabi de Tunis", "user-1", "Sarra", null);
        setField(post, "id", 4L);
        when(postService.searchPublishedPosts("lablabi", 0, 20, null)).thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));

        mockMvcWithRealMapper().perform(get("/api/v1/posts").param("q", "lablabi").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(4))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aTooShortSearchIsA400OnQ() throws Exception {
        when(postService.searchPublishedPosts("a", 0, 20, null))
                .thenThrow(new InvalidParameterException("q", "q must be between 2 and 100 characters"));

        mockMvc.perform(get("/api/v1/posts").param("q", "a").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.q").exists());
    }
}

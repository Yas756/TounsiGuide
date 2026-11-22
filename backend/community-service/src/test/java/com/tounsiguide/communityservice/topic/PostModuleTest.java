package com.tounsiguide.communityservice.topic;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.location.LocationClient;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostController;
import com.tounsiguide.communityservice.post.PostMapper;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PostService;
import com.tounsiguide.communityservice.post.PublicationStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A post's optional topic module (S3-B08): validation, response fields and the feed filter. */
@ExtendWith(MockitoExtension.class)
class PostModuleTest {

    private static final CurrentUser AUTHOR = new CurrentUser("author-1", "Sarra", Set.of("MEMBER"));

    @Mock
    private PostRepository postRepository;
    @Mock
    private LocationClient locationClient;
    @Mock
    private PostChangeNotifier changeNotifier;
    @Mock
    private MediaService mediaService;
    @Mock
    private TopicModuleService moduleService;

    private PostService postService;
    private TopicModule cafes;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, locationClient, changeNotifier, mediaService, moduleService);
        cafes = new TopicModule("cafes", "Cafés", "Où boire un café", "☕", "u", "U");
        setField(cafes, "id", 3L);
        setField(cafes, "status", ModuleStatus.APPROVED);
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aPostCanBeCreatedInAnApprovedModule() {
        Post post = postService.createPost("Bon café", null, 3L, null, AUTHOR);

        assertThat(post.getModuleId()).isEqualTo(3L);
        verify(moduleService).requireSelectable(3L);
    }

    @Test
    void aNonApprovedModuleIsAValidationErrorAndNothingIsSaved() {
        doThrow(new InvalidParameterException("moduleId", "Ce module n'existe pas ou n'est pas approuvé"))
                .when(moduleService).requireSelectable(9L);

        assertThatThrownBy(() -> postService.createPost("Bon café", null, 9L, null, AUTHOR))
                .isInstanceOf(InvalidParameterException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void editingKeepsAnArchivedModuleButANewModuleMustBeApproved() {
        Post post = new Post("Bon café", "author-1", "Sarra", null);
        post.setModuleId(3L);
        setField(post, "id", 12L);
        when(postRepository.findById(12L)).thenReturn(Optional.of(post));

        postService.updatePost(12L, "Très bon café", null, 3L, null, AUTHOR);
        verify(moduleService, never()).requireSelectable(any());

        postService.updatePost(12L, "Très bon café", null, 5L, null, AUTHOR);
        verify(moduleService).requireSelectable(5L);
        assertThat(post.getModuleId()).isEqualTo(5L);

        postService.updatePost(12L, "Très bon café", null, null, null, AUTHOR);
        assertThat(post.getModuleId()).isNull();
    }

    @Test
    void theOldUpdateWithoutModuleKeepsTheModule() {
        Post post = new Post("Bon café", "author-1", "Sarra", null);
        post.setModuleId(3L);
        setField(post, "id", 12L);
        when(postRepository.findById(12L)).thenReturn(Optional.of(post));

        postService.updatePost(12L, "Très bon café", null, List.of(), AUTHOR);

        assertThat(post.getModuleId()).isEqualTo(3L);
    }

    @Test
    void theFeedFilteredByModuleKeepsThePagingContractAndShowsTheModule() throws Exception {
        Post post = new Post("Bon café", "author-1", "Sarra", null);
        post.setModuleId(3L);
        setField(post, "id", 12L);
        when(moduleService.approvedBySlug("cafes")).thenReturn(cafes);
        when(postRepository.findAllByPublicationStatusAndModuleId(eq(PublicationStatus.PUBLISHED), eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));
        when(mediaService.mediaOfPosts(anyList())).thenReturn(Map.of());
        when(moduleService.approvedByIds(anyList())).thenReturn(Map.of(3L, cafes));

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PostController(postService, new PostMapper(), mediaService, moduleService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/posts").param("module", "cafes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moduleId").value(3))
                .andExpect(jsonPath("$.content[0].moduleSlug").value("cafes"))
                .andExpect(jsonPath("$.content[0].moduleName").value("Cafés"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void anUnknownModuleInTheFeedFilterIs404() throws Exception {
        when(moduleService.approvedBySlug("inconnu")).thenThrow(new EntityNotFoundException("Module introuvable"));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PostController(postService, new PostMapper(), mediaService, moduleService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/posts").param("module", "inconnu"))
                .andExpect(status().isNotFound());
    }
}

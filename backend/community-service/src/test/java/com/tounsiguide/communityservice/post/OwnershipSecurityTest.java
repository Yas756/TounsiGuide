package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.communityservice.location.LocationClient;
import com.tounsiguide.communityservice.location.PostChangeNotifier;
import com.tounsiguide.communityservice.media.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S2-T02 security tests at the HTTP level: the real controller, service and identity resolver (only the database and
 * the other services are mocked). No identity → 401; another member's post → 403; the author or an admin may act.
 */
@ExtendWith(MockitoExtension.class)
class OwnershipSecurityTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private LocationClient locationClient;
    @Mock
    private PostChangeNotifier changeNotifier;
    @Mock
    private MediaService mediaService;

    private MockMvc mvc;
    private Post sarrasPost;

    @BeforeEach
    void setUp() {
        PostService service = new PostService(postRepository, locationClient, changeNotifier, mediaService, null);
        mvc = MockMvcBuilders.standaloneSetup(new PostController(service, new PostMapper(), mediaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
        sarrasPost = new Post("Sarra's post", "user-1", "Sarra", null);
        setField(sarrasPost, "id", 1L);
        lenient().when(postRepository.findById(1L)).thenReturn(Optional.of(sarrasPost));
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static final String BODY = "{\"content\":\"Edited\",\"locationId\":null}";

    @Test
    void writingWithoutAnIdentityIs401() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(delete("/api/v1/posts/1"))
                .andExpect(status().isUnauthorized());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void anotherMemberCannotEditOrDeleteThePost() throws Exception {
        mvc.perform(put("/api/v1/posts/1").header("X-User-Id", "user-2").header("X-User-Roles", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/v1/posts/1").header("X-User-Id", "user-2").header("X-User-Roles", "MEMBER"))
                .andExpect(status().isForbidden());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void theAuthorCanEditAndAnAdminCanDelete() throws Exception {
        mvc.perform(put("/api/v1/posts/1").header("X-User-Id", "user-1").header("X-User-Roles", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Edited"));
        mvc.perform(delete("/api/v1/posts/1").header("X-User-Id", "admin-1").header("X-User-Roles", "MEMBER,ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void anAdminCannotEditSomeoneElsesPost() throws Exception {
        mvc.perform(put("/api/v1/posts/1").header("X-User-Id", "admin-1").header("X-User-Roles", "MEMBER,ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
}

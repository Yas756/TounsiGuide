package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.post.dto.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @Mock
    private PostMapper postMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(postService, postMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllPostsReturnsOk() throws Exception {
        when(postService.getAllPosts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getPostByIdReturnsOk() throws Exception {
        Post post = new Post("A post", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null);
        PostResponse response = new PostResponse(
                1L, "A post", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null,
                Instant.now(), Instant.now()
        );
        when(postService.getPostById(1L)).thenReturn(post);
        when(postMapper.toResponse(post)).thenReturn(response);

        mockMvc.perform(get("/api/v1/posts/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

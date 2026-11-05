package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.post.Post;
import com.tounsiguide.communityservice.post.PostService;
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
class CommentControllerTest {

    @Mock
    private CommentService commentService;

    @Mock
    private CommentMapper commentMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(commentService, commentMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllCommentsReturnsOk() throws Exception {
        when(commentService.getAllCommentsForPost(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/posts/1/comments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getCommentByIdReturnsOk() throws Exception {
        Post post = new Post("A post", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, null);
        Comment comment = new Comment(post, "A comment", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME);
        CommentResponse response = new CommentResponse(
                10L, 1L, "A comment", PostService.DEMO_AUTHOR_ID,
                PostService.DEMO_AUTHOR_NAME, Instant.now(), Instant.now()
        );
        when(commentService.getCommentById(1L, 10L)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(response);

        mockMvc.perform(get("/api/v1/posts/1/comments/10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

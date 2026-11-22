package com.tounsiguide.communityservice.comment;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.post.Post;
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

    @Mock
    private MediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(commentService, commentMapper, mediaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllCommentsReturnsOk() throws Exception {
        when(commentService.getAllCommentsForPost(1L, false, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/posts/1/comments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getCommentByIdReturnsOk() throws Exception {
        Post post = new Post("A post", "user-1",
                "Sarra", null);
        Comment comment = new Comment(post, "A comment", "user-1",
                "Sarra");
        CommentResponse response = new CommentResponse(
                10L, 1L, "A comment", "user-1",
                "Sarra", Instant.now(), Instant.now(), List.of()
        );
        when(commentService.getCommentById(1L, 10L)).thenReturn(comment);
        when(commentMapper.toResponse(comment, List.of())).thenReturn(response);

        mockMvc.perform(get("/api/v1/posts/1/comments/10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminListsTheCommentsEvenOfADeletedPost() throws Exception {
        when(commentService.getAllCommentsForPost(1L, true, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/posts/1/comments").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(commentService).getAllCommentsForPost(1L, true, null);
    }
}

package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.post.dto.CreatePostRequest;
import com.tounsiguide.communityservice.post.dto.PostResponse;
import com.tounsiguide.communityservice.post.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;
    private final PostMapper postMapper;

    public PostController(PostService postService, PostMapper postMapper) {
        this.postService = postService;
        this.postMapper = postMapper;
    }

    @GetMapping
    public List<PostResponse> getAllPosts() {
        return postService.getAllPosts().stream()
                .map(postMapper::toResponse)
                .toList();
    }

    @GetMapping("/{postId}")
    public PostResponse getPostById(@PathVariable Long postId) {
        return postMapper.toResponse(postService.getPostById(postId));
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        Post post = postService.createPost(request.content(), request.locationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(postMapper.toResponse(post));
    }

    @PutMapping("/{postId}")
    public PostResponse updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        Post post = postService.updatePost(postId, request.content(), request.locationId());
        return postMapper.toResponse(post);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId) {
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }
}

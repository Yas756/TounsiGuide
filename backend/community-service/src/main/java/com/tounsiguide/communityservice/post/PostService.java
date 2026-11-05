package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.location.DummyLocationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {

    public static final String DEMO_AUTHOR_ID = "demo-user-001";
    public static final String DEMO_AUTHOR_NAME = "Demo User";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final DummyLocationService locationService;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            DummyLocationService locationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.locationService = locationService;
    }

    @Transactional
    public Post createPost(String content, Long locationId) {
        locationService.validateLocationId(locationId);
        Post post = new Post(content, DEMO_AUTHOR_ID, DEMO_AUTHOR_NAME, locationId);
        return postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Post getPostById(Long postId) {
        return findPostOrThrow(postId);
    }

    @Transactional
    public Post updatePost(Long postId, String content, Long locationId) {
        locationService.validateLocationId(locationId);
        Post post = findPostOrThrow(postId);
        post.setContent(content);
        post.setLocationId(locationId);
        return postRepository.save(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = findPostOrThrow(postId);
        commentRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
    }

    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
    }
}

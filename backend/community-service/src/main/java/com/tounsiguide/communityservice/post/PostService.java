package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.comment.CommentRepository;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.location.LocationClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PostService {

    /** Author of the posts created in Sprint 1, before accounts existed. Only an administrator can modify them. */
    public static final String DEMO_AUTHOR_ID = "demo-user-001";
    public static final String DEMO_AUTHOR_NAME = "Demo User";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LocationClient locationClient;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            LocationClient locationClient
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.locationClient = locationClient;
    }

    @Transactional
    public Post createPost(String content, Long locationId, CurrentUser user) {
        locationClient.requireExists(locationId);
        Post post = new Post(content, user.id(), user.displayName(), locationId);
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

    /** Replaces the content and the Location (null removes it). Author only. */
    @Transactional
    public Post updatePost(Long postId, String content, Long locationId, CurrentUser user) {
        Post post = findPostOrThrow(postId);
        user.requireAuthor(post.getAuthorId());
        if (!Objects.equals(locationId, post.getLocationId())) {
            locationClient.requireExists(locationId);
        }
        post.setContent(content);
        post.setLocationId(locationId);
        return postRepository.save(post);
    }

    /** The author or an administrator may delete the post and its comments. */
    @Transactional
    public void deletePost(Long postId, CurrentUser user) {
        Post post = findPostOrThrow(postId);
        user.requireAuthorOrAdmin(post.getAuthorId());
        commentRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
    }

    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
    }
}

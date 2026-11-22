package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.media.MediaService;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.dto.CreatePostRequest;
import com.tounsiguide.communityservice.post.dto.PostResponse;
import com.tounsiguide.communityservice.post.dto.UpdatePostRequest;
import com.tounsiguide.communityservice.topic.TopicModule;
import com.tounsiguide.communityservice.topic.TopicModuleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;
    private final PostMapper postMapper;
    private final MediaService mediaService;
    /** Null only in tests that build the controller by hand without modules. */
    private final TopicModuleService moduleService;

    @Autowired
    public PostController(PostService postService, PostMapper postMapper, MediaService mediaService,
                          TopicModuleService moduleService) {
        this.postService = postService;
        this.postMapper = postMapper;
        this.mediaService = mediaService;
        this.moduleService = moduleService;
    }

    /** Without topic modules (tests): the module fields of the responses stay null. */
    public PostController(PostService postService, PostMapper postMapper, MediaService mediaService) {
        this(postService, postMapper, mediaService, null);
    }

    /**
     * One page of the feed, newest first: {@code ?page=0&size=20} (size 1 to 50). {@code ?module=slug} keeps only
     * the posts of that approved topic module (404 for an unknown or non-approved module), same paging.
     * {@code ?q=text} (S3-US10, unified search) keeps only the posts whose text or author name contains it
     * (2 to 100 characters, 400 {@code details.q} otherwise); it can be combined with {@code module}.
     */
    @GetMapping
    public PageResponse<PostResponse> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PostService.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String q
    ) {
        Long moduleId = module == null || module.isBlank() || moduleService == null
                ? null : moduleService.approvedBySlug(module.trim()).getId();
        Page<Post> posts = q == null
                ? postService.getPublishedPosts(page, size, moduleId)
                : postService.searchPublishedPosts(q, page, size, moduleId);
        // The media and modules of all the posts of the page in one query each.
        Map<Long, List<MediaResponse>> media = mediaService.mediaOfPosts(posts.map(Post::getId).getContent());
        Map<Long, TopicModule> modules = modulesOf(posts.getContent());
        return PageResponse.of(posts, post -> postMapper.toResponse(post, media.getOrDefault(post.getId(), List.of()),
                moduleOf(modules, post)));
    }

    /**
     * Several published posts at once, in the order asked: {@code ?ids=3,1,2} (at most 50). Unknown, deleted and
     * restricted posts are left out (no 404). Read by the Business Service for page walls (S3-B04).
     */
    @GetMapping("/batch")
    public List<PostResponse> getPostsByIds(@RequestParam List<Long> ids) {
        List<Post> posts = postService.getPublishedPostsByIds(ids);
        Map<Long, List<MediaResponse>> media = mediaService.mediaOfPosts(posts.stream().map(Post::getId).toList());
        Map<Long, TopicModule> modules = modulesOf(posts);
        return posts.stream()
                .map(post -> postMapper.toResponse(post, media.getOrDefault(post.getId(), List.of()),
                        moduleOf(modules, post)))
                .toList();
    }

    /**
     * Includes the media: other services (e.g. the AI of the Location Service) read the image URLs here.
     * A deleted post answers 404, except to an administrator; a restricted one too, except to its author.
     */
    @GetMapping("/{postId}")
    public PostResponse getPostById(
            @PathVariable Long postId,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId
    ) {
        return withMedia(postService.getPostById(postId, CurrentUser.isAdminRoles(roles), userId));
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            CurrentUser user
    ) {
        Post post = postService.createPost(
                request.content(), request.locationId(), request.moduleId(), request.mediaIds(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(withMedia(post));
    }

    /** Replaces content, Location and topic module (null removes them). */
    @PutMapping("/{postId}")
    public PostResponse updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request,
            CurrentUser user
    ) {
        Post post = postService.updatePost(
                postId, request.content(), request.locationId(), request.moduleId(), request.mediaIds(), user);
        return withMedia(post);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId, CurrentUser user) {
        postService.deletePost(postId, user);
        return ResponseEntity.noContent().build();
    }

    private PostResponse withMedia(Post post) {
        return postMapper.toResponse(post, mediaService.mediaOfPost(post.getId()),
                moduleOf(modulesOf(List.of(post)), post));
    }

    private Map<Long, TopicModule> modulesOf(Collection<Post> posts) {
        if (moduleService == null) {
            return Map.of();
        }
        return moduleService.approvedByIds(posts.stream().map(Post::getModuleId).toList());
    }

    private static TopicModule moduleOf(Map<Long, TopicModule> modules, Post post) {
        return post.getModuleId() == null ? null : modules.get(post.getModuleId());
    }
}

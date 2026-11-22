package com.tounsiguide.locationservice.community;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tounsiguide.locationservice.common.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads posts and comments from the Community Service (8081). The Location Service never writes there:
 * {@code Post.locationId} stays the author's claim and is only read (association-api.md, section 7).
 */
@Component
public class CommunityClient {

    /** Size of the pages read by {@link #findAllPosts()} (the Community Service's maximum). */
    static final int PAGE_SIZE = 50;
    /** Safety stop for {@link #findAllPosts()}: 50 x 2,000 = 100,000 posts. */
    static final int MAX_PAGES = 2_000;
    private static final ParameterizedTypeReference<List<CommentSnapshot>> COMMENTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public CommunityClient(
            @Value("${tounsiguide.community.url:http://localhost:8081}") String communityUrl,
            @Value("${tounsiguide.security.internal-token}") String internalToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(communityUrl).requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
    }

    /**
     * @return the post, or empty when the Community Service answers 404 (the post was deleted)
     * @throws CommunityServiceUnavailableException when it cannot be reached or fails
     */
    public Optional<PostSnapshot> findPost(long postId) {
        try {
            return Optional.ofNullable(restClient.get().uri("/api/v1/posts/{id}", postId).retrieve().body(PostSnapshot.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new CommunityServiceUnavailableException(exception);
        }
    }

    /** Comments of a post, oldest first; empty when the post does not exist. */
    public List<CommentSnapshot> findComments(long postId) {
        try {
            List<CommentSnapshot> comments = restClient.get().uri("/api/v1/posts/{id}/comments", postId)
                    .retrieve().body(COMMENTS);
            return Objects.requireNonNullElse(comments, List.of());
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
        } catch (RestClientException exception) {
            throw new CommunityServiceUnavailableException(exception);
        }
    }

    /**
     * Every published post (used once by the startup migration). {@code GET /api/v1/posts} is paged since
     * 2026-11-15, so the pages are read one after the other, newest first.
     */
    public List<PostSnapshot> findAllPosts() {
        List<PostSnapshot> posts = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                PostPage result = restClient.get()
                        .uri("/api/v1/posts?page={page}&size={size}", page, PAGE_SIZE)
                        .retrieve().body(PostPage.class);
                if (result == null || result.content() == null) {
                    break;
                }
                posts.addAll(result.content());
                if (result.content().isEmpty() || page + 1 >= result.totalPages()) {
                    break;
                }
            }
        } catch (RestClientException exception) {
            throw new CommunityServiceUnavailableException(exception);
        }
        return posts;
    }

    /** The Community Service's page of posts ({@code content, page, size, totalElements, totalPages}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostPage(List<PostSnapshot> content, int page, int size, long totalElements, int totalPages) {
    }

    /** The fields of the Community Service's PostResponse that this service uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostSnapshot(
            Long id,
            String content,
            String authorId,
            String authorName,
            Long locationId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** The fields of the Community Service's CommentResponse that this service uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommentSnapshot(
            Long id,
            Long postId,
            String content,
            String authorId,
            String authorName,
            Instant createdAt
    ) {
    }
}

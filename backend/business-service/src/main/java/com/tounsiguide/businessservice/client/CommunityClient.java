package com.tounsiguide.businessservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads posts from the Community Service (8081), read only. The Business Service never changes a post and never
 * copies one into its own database: hiding a post from a wall is stored here as a separate override (S3-B05).
 */
@Component
public class CommunityClient {

    /** Ids per call to {@code GET /api/v1/posts/batch} (the Community Service's maximum). */
    static final int BATCH_SIZE = 50;

    private static final ParameterizedTypeReference<List<PostSnapshot>> POSTS = new ParameterizedTypeReference<>() {
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
     * The published posts among these ids (deleted ones are left out by the Community Service), in no
     * particular order.
     */
    public List<PostSnapshot> findPublishedPosts(Collection<Long> postIds) {
        List<Long> ids = postIds.stream().distinct().toList();
        List<PostSnapshot> posts = new ArrayList<>();
        try {
            for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
                String chunk = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE)).stream()
                        .map(String::valueOf).collect(Collectors.joining(","));
                List<PostSnapshot> found = restClient.get().uri("/api/v1/posts/batch?ids={ids}", chunk)
                        .retrieve().body(POSTS);
                if (found != null) {
                    posts.addAll(found);
                }
            }
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("COMMUNITY_SERVICE_UNAVAILABLE",
                    "Le service des publications est temporairement indisponible", exception);
        }
        return posts;
    }

    /** The fields of the Community Service's PostResponse shown on a wall; media are passed on as they are. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostSnapshot(
            Long id,
            String content,
            String authorId,
            String authorName,
            Long locationId,
            Instant createdAt,
            Instant updatedAt,
            List<Map<String, Object>> media
    ) {
    }
}

package com.tounsiguide.locationservice.contract;

import com.tounsiguide.locationservice.community.CommunityClient.CommentSnapshot;
import com.tounsiguide.locationservice.community.CommunityClient.PostPage;
import com.tounsiguide.locationservice.community.CommunityClient.PostSnapshot;
import com.tounsiguide.locationservice.location.LocationSource;
import com.tounsiguide.locationservice.location.dto.LocationResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract with the Community Service (S2-T03), against the shared samples in docs/api/contract-samples:
 * this service can read the posts, pages and comments the Community Service answers, and its LocationResponse has
 * exactly the documented shape.
 */
class LocationContractTest {

    private static final Path SAMPLES = Path.of("..", "..", "docs", "api", "contract-samples");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String sample(String name) throws IOException {
        return Files.readString(SAMPLES.resolve(name));
    }

    @Test
    void thePostSampleGivesEveryFieldTheAnalysisUses() throws IOException {
        PostSnapshot post = JSON.readValue(sample("post-response.json"), PostSnapshot.class);

        assertThat(post.id()).isEqualTo(12L);
        assertThat(post.content()).isNotBlank();
        assertThat(post.authorId()).isNotBlank();
        assertThat(post.authorName()).isEqualTo("Sarra");
        assertThat(post.locationId()).isEqualTo(14L);
        assertThat(post.createdAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(post.updatedAt()).isNotNull();
    }

    @Test
    void theFeedPageSampleIsReadPageByPage() throws IOException {
        PostPage page = JSON.readValue(sample("post-page.json"), PostPage.class);

        assertThat(page.content()).singleElement().satisfies(p -> assertThat(p.id()).isEqualTo(12L));
        assertThat(page.page()).isZero();
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void theCommentSampleGivesTheTextAndAuthor() throws IOException {
        CommentSnapshot[] comments = JSON.readValue(sample("comment-list.json"), CommentSnapshot[].class);

        assertThat(comments).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(88L);
            assertThat(c.postId()).isEqualTo(12L);
            assertThat(c.content()).contains("Café Roma");
            assertThat(c.authorId()).isNotBlank();
            assertThat(c.createdAt()).isNotNull();
        });
    }

    @Test
    void theLocationResponseMatchesTheSample() throws IOException {
        LocationResponse location = new LocationResponse(14L, "Café Roma", "Tunis", "Rue de Rome", 36.8, 10.18, "cafe",
                LocationSource.DIRECTORY, List.of("Roma"), Instant.parse("2026-11-08T15:33:13Z"));

        JsonNode expected = JSON.readTree(sample("location-response.json"));
        JsonNode actual = JSON.readTree(JSON.writeValueAsString(location));

        assertThat(shape(actual, "$")).isEqualTo(shape(expected, "$"));
    }

    /** Every JSON path with its type; arrays are described by their first item. */
    private static Map<String, String> shape(JsonNode node, String path) {
        Map<String, String> shape = new TreeMap<>();
        shape.put(path, node.getNodeType().name());
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                shape.putAll(shape(field.getValue(), path + "." + field.getKey()));
            }
        } else if (node.isArray() && !node.isEmpty()) {
            shape.putAll(shape(node.get(0), path + "[]"));
        }
        return shape;
    }
}

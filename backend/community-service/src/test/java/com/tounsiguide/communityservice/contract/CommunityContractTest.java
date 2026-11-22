package com.tounsiguide.communityservice.contract;

import com.tounsiguide.communityservice.comment.dto.CommentResponse;
import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.media.MediaKind;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.PublicationStatus;
import com.tounsiguide.communityservice.post.dto.PostResponse;
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
 * Contract with the Location Service (S2-T03): what this service answers has exactly the shape of the shared samples
 * in docs/api/contract-samples, which the Location Service's own test reads with its client.
 */
class CommunityContractTest {

    private static final Path SAMPLES = Path.of("..", "..", "docs", "api", "contract-samples");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Instant AT = Instant.parse("2026-11-15T10:00:00Z");
    private static final MediaResponse PHOTO = new MediaResponse("m-1", MediaKind.IMAGE, MediaResponse.fileUrl("m-1"),
            "image/jpeg", 482113);
    private static final PostResponse POST = new PostResponse(12L, "Great coffee", "user-1", "Sarra", 14L, AT, AT,
            PublicationStatus.PUBLISHED, List.of(PHOTO));

    @Test
    void thePostResponseMatchesTheSample() throws IOException {
        assertSameShape(POST, "post-response.json");
    }

    @Test
    void theFeedPageMatchesTheSample() throws IOException {
        assertSameShape(new PageResponse<>(List.of(POST), 0, 20, 1, 1), "post-page.json");
    }

    @Test
    void theCommentListMatchesTheSample() throws IOException {
        CommentResponse comment = new CommentResponse(88L, 12L, "At Café Roma", "user-2", "Ali", AT, AT, List.of(PHOTO));
        assertSameShape(List.of(comment), "comment-list.json");
    }

    @Test
    void theLocationSampleHasWhatTheLocationClientReliesOn() throws IOException {
        // LocationClient only uses the status (200 = exists, 404 = unknown); the id is what a post stores.
        JsonNode location = JSON.readTree(Files.readString(SAMPLES.resolve("location-response.json")));
        assertThat(location.get("id").isIntegralNumber()).isTrue();
    }

    private static void assertSameShape(Object produced, String sample) throws IOException {
        JsonNode expected = JSON.readTree(Files.readString(SAMPLES.resolve(sample)));
        JsonNode actual = JSON.readTree(JSON.writeValueAsString(produced));
        assertThat(shape(actual, "$")).as("shape of %s", sample).isEqualTo(shape(expected, "$"));
    }

    /** Every JSON path with its type ("$.media[].sizeBytes" -> NUMBER); arrays are described by their first item. */
    static Map<String, String> shape(JsonNode node, String path) {
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

package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.post.dto.PostResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostMapperTest {

    private final PostMapper mapper = new PostMapper();

    @Test
    void toResponseIncludesThePlaceWhenThePostHasCoordinates() {
        Post post = new Post("A post", PostService.DEMO_AUTHOR_ID, PostService.DEMO_AUTHOR_NAME, null);
        post.setPlace("Café des Nattes", "Sidi Bou Said", 36.8702, 10.3416, "place-123");

        PostResponse response = mapper.toResponse(post);

        assertThat(response.place()).isNotNull();
        assertThat(response.place().name()).isEqualTo("Café des Nattes");
        assertThat(response.place().latitude()).isEqualTo(36.8702);
        assertThat(response.place().googlePlaceId()).isEqualTo("place-123");
    }

    @Test
    void toResponseHasNoPlaceWhenThePostHasNoCoordinates() {
        Post post = new Post("A post", PostService.DEMO_AUTHOR_ID, PostService.DEMO_AUTHOR_NAME, null);

        assertThat(mapper.toResponse(post).place()).isNull();
    }
}

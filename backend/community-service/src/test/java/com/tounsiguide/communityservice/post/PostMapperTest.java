package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.post.dto.PostResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostMapperTest {

    private final PostMapper mapper = new PostMapper();

    @Test
    void toResponseCopiesTheLocationIdOnly() {
        Post post = new Post("A post", "user-1", "Sarra", 4L);

        PostResponse response = mapper.toResponse(post);

        assertThat(response.content()).isEqualTo("A post");
        assertThat(response.authorName()).isEqualTo("Sarra");
        assertThat(response.locationId()).isEqualTo(4L);
    }

    @Test
    void toResponseHasNoLocationWhenThePostHasNone() {
        Post post = new Post("A post", "user-1", "Sarra", null);

        assertThat(mapper.toResponse(post).locationId()).isNull();
    }
}

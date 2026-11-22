package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.media.dto.MediaResponse;
import com.tounsiguide.communityservice.post.dto.PostResponse;
import com.tounsiguide.communityservice.topic.TopicModule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostMapper {

    /** A post without photos or videos. */
    public PostResponse toResponse(Post post) {
        return toResponse(post, List.of());
    }

    public PostResponse toResponse(Post post, List<MediaResponse> media) {
        return toResponse(post, media, null);
    }

    /** @param module the post's module when it is APPROVED, else null (the module fields stay null) */
    public PostResponse toResponse(Post post, List<MediaResponse> media, TopicModule module) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorName(),
                post.getLocationId(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getPublicationStatus(),
                media == null ? List.of() : media,
                module == null ? null : module.getId(),
                module == null ? null : module.getSlug(),
                module == null ? null : module.getName()
        );
    }
}

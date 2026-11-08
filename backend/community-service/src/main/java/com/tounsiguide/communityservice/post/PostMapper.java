package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.post.dto.PostResponse;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    public PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorName(),
                post.getLocationId(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}

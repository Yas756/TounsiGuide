package com.tounsiguide.communityservice.post;

import com.tounsiguide.communityservice.post.dto.PlaceDto;
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
                toPlace(post),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private PlaceDto toPlace(Post post) {
        if (post.getLatitude() == null || post.getLongitude() == null) {
            return null;
        }
        return new PlaceDto(
                post.getPlaceName(),
                post.getPlaceAddress(),
                post.getLatitude(),
                post.getLongitude(),
                post.getGooglePlaceId()
        );
    }
}
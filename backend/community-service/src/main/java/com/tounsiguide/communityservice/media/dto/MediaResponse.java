package com.tounsiguide.communityservice.media.dto;

import com.tounsiguide.communityservice.media.Media;
import com.tounsiguide.communityservice.media.MediaKind;

/**
 * A stored photo or video.
 *
 * @param url path of the file on the Gateway ({@code /api/v1/media/{id}/file}), relative so that it works
 *            whatever the host; the frontend prefixes it with the Gateway address
 */
public record MediaResponse(
        String id,
        MediaKind type,
        String url,
        String contentType,
        long sizeBytes
) {

    public static String fileUrl(String mediaId) {
        return "/api/v1/media/" + mediaId + "/file";
    }

    public static MediaResponse of(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getType(),
                fileUrl(media.getId()),
                media.getContentType(),
                media.getSizeBytes()
        );
    }
}

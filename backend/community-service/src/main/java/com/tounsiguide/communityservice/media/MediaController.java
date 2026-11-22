package com.tounsiguide.communityservice.media;

import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.media.dto.MediaResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * Step 1: upload one photo or video (multipart field {@code file}). The returned id is then sent in
     * {@code mediaIds} when creating or editing a post or a comment. The user parameter comes first so an
     * anonymous call gets 401 before any check of the file.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(CurrentUser user, @RequestParam("file") MultipartFile file) {
        Media media = mediaService.upload(file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.of(media));
    }

    @GetMapping("/{mediaId}")
    public MediaResponse getMedia(@PathVariable String mediaId) {
        return MediaResponse.of(mediaService.getMedia(mediaId));
    }

    /**
     * The file itself. Public, cached for a year (a media id never changes content) and served with Range support
     * (Spring answers 206 Partial Content to a Range request), which video players need to seek.
     */
    @GetMapping("/{mediaId}/file")
    public ResponseEntity<Resource> getFile(@PathVariable String mediaId) {
        Media media = mediaService.getMedia(mediaId);
        Resource file = mediaService.loadFile(media);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }
}

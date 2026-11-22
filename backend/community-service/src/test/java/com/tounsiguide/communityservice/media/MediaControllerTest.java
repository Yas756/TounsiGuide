package com.tounsiguide.communityservice.media;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.communityservice.post.PublicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaRepository mediaRepository;

    private final InMemoryMediaStorage storage = new InMemoryMediaStorage();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MediaService mediaService =
                new MediaService(mediaRepository, storage, DataSize.ofMegabytes(10), DataSize.ofMegabytes(50));
        lenient().when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(mediaService))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aLoggedInUserCanUploadAPhoto() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "IMG_0001.png", "image/png", MediaFormatTest.PNG);

        mockMvc.perform(multipart("/api/v1/media").file(file).header(CurrentUser.ID_HEADER, "user-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(MediaFormatTest.PNG.length))
                .andExpect(jsonPath("$.url", startsWith("/api/v1/media/")))
                .andExpect(jsonPath("$.url", endsWith("/file")));
    }

    @Test
    void aFileOfAnotherFormatIsRejectedWithAValidationError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "GIF89a......".getBytes());

        mockMvc.perform(multipart("/api/v1/media").file(file).header(CurrentUser.ID_HEADER, "user-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.file").value(MediaService.UNSUPPORTED_FORMAT));
    }

    @Test
    void aRequestWithoutFileIsAValidationError() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").header(CurrentUser.ID_HEADER, "user-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.file").exists());
    }

    @Test
    void anAnonymousUploadIsRefused() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", MediaFormatTest.PNG);

        mockMvc.perform(multipart("/api/v1/media").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(mediaRepository, never()).save(any());
    }

    private Media storedVideo(byte[] content) {
        String key = storage.store(new ByteArrayInputStream(content), ".mp4");
        Media media = new Media(MediaKind.VIDEO, "video/mp4", content.length, key, "user-1");
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        return media;
    }

    @Test
    void theFileIsServedWithItsContentTypeAndCacheHeaders() throws Exception {
        byte[] video = Arrays.copyOf(MediaFormatTest.MP4, 100);
        Media media = storedVideo(video);

        mockMvc.perform(get("/api/v1/media/{id}/file", media.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(video))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void aRangeRequestGetsPartialContent() throws Exception {
        byte[] video = Arrays.copyOf(MediaFormatTest.MP4, 100);
        Media media = storedVideo(video);

        mockMvc.perform(get("/api/v1/media/{id}/file", media.getId()).header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-9/100"))
                .andExpect(content().bytes(Arrays.copyOf(video, 10)));
    }

    @Test
    void anUnknownMediaIsNotFound() throws Exception {
        when(mediaRepository.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/media/nope/file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void theMetadataIsPublic() throws Exception {
        Media media = storedVideo(MediaFormatTest.MP4);

        mockMvc.perform(get("/api/v1/media/{id}", media.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VIDEO"))
                .andExpect(jsonPath("$.url").value("/api/v1/media/" + media.getId() + "/file"));
    }

    @Test
    void theMediaOfADeletedPostOrOfItsCommentsIsNotFound() throws Exception {
        Media media = storedVideo(MediaFormatTest.MP4);
        media.attach(5L, null, 0);
        when(mediaRepository.existsByIdAndPostStatus(media.getId(), PublicationStatus.DELETED)).thenReturn(true);

        mockMvc.perform(get("/api/v1/media/{id}/file", media.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/media/{id}", media.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theMediaOfAPublishedPostIsStillServed() throws Exception {
        Media media = storedVideo(MediaFormatTest.MP4);
        media.attach(5L, null, 0);
        when(mediaRepository.existsByIdAndPostStatus(media.getId(), PublicationStatus.DELETED)).thenReturn(false);

        mockMvc.perform(get("/api/v1/media/{id}/file", media.getId()))
                .andExpect(status().isOk());
    }
}

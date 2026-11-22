package com.tounsiguide.communityservice.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFormatTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 1, 0, 0, 0};

    @Test
    void recognizesTheAcceptedFormatsFromTheirFirstBytes() {
        assertThat(MediaFormat.detect(JPEG)).contains(MediaFormat.JPEG);
        assertThat(MediaFormat.detect(PNG)).contains(MediaFormat.PNG);
        assertThat(MediaFormat.detect(WEBP)).contains(MediaFormat.WEBP);
        assertThat(MediaFormat.detect(MP4)).contains(MediaFormat.MP4);
        assertThat(MediaFormat.detect(WEBM)).contains(MediaFormat.WEBM);
    }

    @Test
    void givesTheKindTheContentTypeAndTheExtension() {
        assertThat(MediaFormat.PNG.kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(MediaFormat.MP4.kind()).isEqualTo(MediaKind.VIDEO);
        assertThat(MediaFormat.WEBP.contentType()).isEqualTo("image/webp");
        assertThat(MediaFormat.JPEG.extension()).isEqualTo(".jpg");
    }

    @Test
    void rejectsOtherFilesEvenWhenTheyAreNamedLikeAPhoto() {
        assertThat(MediaFormat.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(MediaFormat.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(MediaFormat.detect("%PDF-1.7....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // RIFF but not WebP (a WAV file).
        assertThat(MediaFormat.detect("RIFF\u0000\u0000\u0000\u0000WAVE".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    @Test
    void rejectsEmptyOrTooShortHeaders() {
        assertThat(MediaFormat.detect(new byte[0])).isEmpty();
        assertThat(MediaFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(MediaFormat.detect("RIFF".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(MediaFormat.detect(null)).isEmpty();
    }
}

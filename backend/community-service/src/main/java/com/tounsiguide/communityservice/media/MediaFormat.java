package com.tounsiguide.communityservice.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The accepted file formats, recognized from their first bytes ("magic bytes"). The file name and the
 * Content-Type sent by the browser are never trusted: a renamed .exe is not a photo.
 */
public enum MediaFormat {
    JPEG("image/jpeg", ".jpg", MediaKind.IMAGE),
    PNG("image/png", ".png", MediaKind.IMAGE),
    WEBP("image/webp", ".webp", MediaKind.IMAGE),
    MP4("video/mp4", ".mp4", MediaKind.VIDEO),
    WEBM("video/webm", ".webm", MediaKind.VIDEO);

    /** Number of leading bytes {@link #detect(byte[])} needs. */
    public static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP_TAG = ascii("WEBP");
    private static final byte[] FTYP = ascii("ftyp");
    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    private final String contentType;
    private final String extension;
    private final MediaKind kind;

    MediaFormat(String contentType, String extension, MediaKind kind) {
        this.contentType = contentType;
        this.extension = extension;
        this.kind = kind;
    }

    public String contentType() {
        return contentType;
    }

    /** Extension used for the stored file, with the leading dot. */
    public String extension() {
        return extension;
    }

    public MediaKind kind() {
        return kind;
    }

    /**
     * @param header the first bytes of the file (at least {@link #HEADER_LENGTH} for WebP and MP4; fewer is fine
     *               for a too-short file, which is simply not recognized)
     */
    public static Optional<MediaFormat> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        if (startsWith(header, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(header, 0, RIFF) && startsWith(header, 8, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        if (startsWith(header, 4, FTYP)) {
            return Optional.of(MP4);
        }
        if (startsWith(header, 0, EBML_MAGIC)) {
            return Optional.of(WEBM);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}

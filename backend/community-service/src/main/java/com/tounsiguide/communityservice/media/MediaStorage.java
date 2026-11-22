package com.tounsiguide.communityservice.media;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Where the bytes of the photos and videos live. Only the storage key is kept in the database, so moving to
 * S3, MinIO or Cloudinary means adding one implementation selected by {@code tounsiguide.media.storage}.
 */
public interface MediaStorage {

    /**
     * Stores the whole stream.
     *
     * @param extension extension with the leading dot, e.g. ".jpg"
     * @return the key to give to {@link #load} and {@link #delete}
     */
    String store(InputStream in, String extension);

    /** @throws jakarta.persistence.EntityNotFoundException when there is no file for this key */
    Resource load(String key);

    /** Does nothing when the file is already gone. */
    void delete(String key);
}

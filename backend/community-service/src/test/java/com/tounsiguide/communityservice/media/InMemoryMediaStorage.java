package com.tounsiguide.communityservice.media;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link MediaStorage} for tests, keeping the files in memory. */
class InMemoryMediaStorage implements MediaStorage {

    final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public String store(InputStream in, String extension) {
        String key = UUID.randomUUID() + extension;
        try {
            files.put(key, in.readAllBytes());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        byte[] content = files.get(key);
        if (content == null) {
            throw new EntityNotFoundException("Media file not found: " + key);
        }
        return new ByteArrayResource(content);
    }

    @Override
    public void delete(String key) {
        files.remove(key);
    }
}

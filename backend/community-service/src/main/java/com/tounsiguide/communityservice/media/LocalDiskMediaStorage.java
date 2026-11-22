package com.tounsiguide.communityservice.media;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps the files in a folder of the server ({@code tounsiguide.media.dir}). File names are a random UUID plus
 * the extension of the detected format: the name chosen by the user is never used, and a key that does not have
 * exactly that shape is refused, so a key can never point outside the folder.
 */
@Component
@ConditionalOnProperty(name = "tounsiguide.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalDiskMediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskMediaStorage.class);

    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9]{1,5}");
    private static final Pattern KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{1,5}");

    private final Path root;

    public LocalDiskMediaStorage(@Value("${tounsiguide.media.dir:${user.home}/tounsiguide-media}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create the media folder " + root, exception);
        }
        log.info("Media files are stored in {}", root);
    }

    @Override
    public String store(InputStream in, String extension) {
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid extension: " + extension);
        }
        String key = UUID.randomUUID() + extension;
        Path target = resolve(key);
        Path temporary = root.resolve(key + ".part");
        try {
            Files.copy(in, temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(target);
            throw new UncheckedIOException("Could not store the media file", exception);
        }
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new EntityNotFoundException("Media file not found: " + key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete the media file " + key, exception);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new EntityNotFoundException("Media file not found: " + key);
        }
        Path file = root.resolve(key).normalize();
        if (!file.getParent().equals(root)) {
            throw new EntityNotFoundException("Media file not found: " + key);
        }
        return file;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort: the partial file is harmless.
        }
    }
}

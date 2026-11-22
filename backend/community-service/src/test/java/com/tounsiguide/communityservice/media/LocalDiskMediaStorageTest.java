package com.tounsiguide.communityservice.media;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskMediaStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesUnderARandomNameAndLoadsAndDeletes() throws Exception {
        LocalDiskMediaStorage storage = new LocalDiskMediaStorage(dir.resolve("media").toString());

        String key = storage.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), ".png");

        assertThat(key).matches("[0-9a-f-]{36}\\.png");
        assertThat(storage.load(key).getContentAsByteArray()).containsExactly(1, 2, 3);
        storage.delete(key);
        try (var files = Files.list(dir.resolve("media"))) {
            assertThat(files).isEmpty();
        }
        storage.delete(key); // already gone: no error
    }

    @Test
    void refusesKeysThatCouldLeaveTheFolder() throws Exception {
        Files.writeString(dir.resolve("secret.txt"), "secret");
        LocalDiskMediaStorage storage = new LocalDiskMediaStorage(dir.resolve("media").toString());

        assertThatThrownBy(() -> storage.load("../secret.txt")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> storage.load("..\\secret.txt")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[]{1}), "/../../x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.tounsiguide.locationservice.geocoding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Small in-memory cache with expiry. The public geocoder asks for fair use, and the same searches
 * come back often (people type the same names), so answers are kept for a few minutes.
 */
final class TtlCache<V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    TtlCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    /** Returns the cached value, or computes, stores and returns it. Failures are not cached. */
    V get(String key, Supplier<V> loader) {
        Instant now = clock.instant();
        Entry<V> entry = entries.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return entry.value();
        }
        V value = loader.get();
        if (entries.size() >= maxEntries) {
            entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
            if (entries.size() >= maxEntries) {
                entries.clear();
            }
        }
        entries.put(key, new Entry<>(value, now.plus(ttl)));
        return value;
    }
}

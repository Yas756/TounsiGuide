package com.tounsiguide.locationservice.geocoding;

import com.tounsiguide.locationservice.geocoding.PhotonGeocodingProvider.Feature;
import com.tounsiguide.locationservice.geocoding.PhotonGeocodingProvider.Geometry;
import com.tounsiguide.locationservice.geocoding.PhotonGeocodingProvider.PhotonResponse;
import com.tounsiguide.locationservice.geocoding.PhotonGeocodingProvider.Properties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PhotonGeocodingProviderTest {

    private static Feature feature(String name, String street, String city, String osmType, Long osmId, String value) {
        return new Feature(new Geometry(List.of(10.3492, 36.8702)),
                new Properties(name, null, street, null, null, city, null, "Tunis", "Tunisie",
                        osmType, osmId, "amenity", value));
    }

    @Test
    void convertsGeoJsonFeatureToPlace() {
        GeocodedPlace place = PhotonGeocodingProvider.toPlace(
                feature("Café des Délices", "Rue Hédi Zarrouk", "Sidi Bou Saïd", "N", 42L, "cafe"));

        assertThat(place).isNotNull();
        assertThat(place.name()).isEqualTo("Café des Délices");
        assertThat(place.latitude()).isEqualTo(36.8702);
        assertThat(place.longitude()).isEqualTo(10.3492);
        assertThat(place.city()).isEqualTo("Sidi Bou Saïd");
        assertThat(place.address()).isEqualTo("Rue Hédi Zarrouk, Sidi Bou Saïd, Tunisie");
        assertThat(place.category()).isEqualTo("cafe");
        assertThat(place.externalId()).isEqualTo("osm:N42");
    }

    @Test
    void usesStreetOrCityWhenThereIsNoNameAndDropsDuplicates() {
        List<GeocodedPlace> places = PhotonGeocodingProvider.toPlaces(new PhotonResponse(List.of(
                feature(null, "Rue de Marseille", "Tunis", "W", 7L, "residential"),
                feature(null, "Rue de Marseille", "Tunis", "W", 7L, "residential"),
                new Feature(null, null))));

        assertThat(places).hasSize(1);
        assertThat(places.get(0).name()).isEqualTo("Rue de Marseille");
        assertThat(places.get(0).address()).isEqualTo("Tunis, Tunisie");
    }

    @Test
    void cacheReturnsStoredValueUntilItExpires() {
        MutableClock clock = new MutableClock();
        TtlCache<String> cache = new TtlCache<>(Duration.ofMinutes(10), 10, clock);
        AtomicInteger calls = new AtomicInteger();

        cache.get("k", () -> "v" + calls.incrementAndGet());
        cache.get("k", () -> "v" + calls.incrementAndGet());
        assertThat(calls).hasValue(1);

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThat(cache.get("k", () -> "v" + calls.incrementAndGet())).isEqualTo("v2");
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

package com.tounsiguide.locationservice.geocoding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** {@code tounsiguide.geocoding.provider=none}: only the Location directory is searched (offline demo, tests). */
@Component
@ConditionalOnProperty(name = "tounsiguide.geocoding.provider", havingValue = "none")
public class DisabledGeocodingProvider implements GeocodingProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public List<GeocodedPlace> search(String query, int limit, Double biasLatitude, Double biasLongitude) {
        return List.of();
    }

    @Override
    public Optional<GeocodedPlace> reverse(double latitude, double longitude) {
        return Optional.empty();
    }
}

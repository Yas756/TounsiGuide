package com.tounsiguide.locationservice.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Geocoding with Photon (komoot): OpenStreetMap data, search-as-you-type, no API key.
 * The public server asks for fair use; answers are cached and the frontend waits for a pause in typing.
 * A self-hosted Photon (or another provider) can be configured without changing callers.
 */
@Component
@ConditionalOnProperty(name = "tounsiguide.geocoding.provider", havingValue = "photon", matchIfMissing = true)
public class PhotonGeocodingProvider implements GeocodingProvider {

    private static final int CACHE_SIZE = 500;

    private final RestClient restClient;
    private final GeocodingProperties properties;
    private final TtlCache<List<GeocodedPlace>> cache;

    public PhotonGeocodingProvider(GeocodingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        this.properties = properties;
        this.cache = new TtlCache<>(properties.cacheTtl(), CACHE_SIZE, Clock.systemUTC());
    }

    @Override
    public String name() {
        return "photon";
    }

    @Override
    public List<GeocodedPlace> search(String query, int limit, Double biasLatitude, Double biasLongitude) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.photon().url())
                .path("/api/")
                .queryParam("q", query)
                .queryParam("limit", limit)
                .queryParam("lang", properties.language())
                .queryParam("bbox", properties.photon().bbox());
        if (biasLatitude != null && biasLongitude != null) {
            uri.queryParam("lat", biasLatitude).queryParam("lon", biasLongitude);
        }
        URI target = uri.encode().build().toUri();
        return cache.get(target.toString(), () -> toPlaces(fetch(target)));
    }

    @Override
    public Optional<GeocodedPlace> reverse(double latitude, double longitude) {
        URI target = UriComponentsBuilder.fromUriString(properties.photon().url())
                .path("/reverse")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("lang", properties.language())
                .queryParam("limit", 1)
                .encode().build().toUri();
        return cache.get(target.toString(), () -> toPlaces(fetch(target))).stream().findFirst();
    }

    private PhotonResponse fetch(URI target) {
        try {
            PhotonResponse response = restClient.get().uri(target).retrieve().body(PhotonResponse.class);
            return response == null ? new PhotonResponse(List.of()) : response;
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException("Photon geocoder unavailable", exception);
        }
    }

    /** Keeps features with a usable name and position, one per OpenStreetMap object. */
    static List<GeocodedPlace> toPlaces(PhotonResponse response) {
        List<GeocodedPlace> places = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Feature feature : Objects.requireNonNullElse(response.features(), List.<Feature>of())) {
            GeocodedPlace place = toPlace(feature);
            if (place != null && seen.add(place.externalId() != null ? place.externalId() : place.name())) {
                places.add(place);
            }
        }
        return places;
    }

    static GeocodedPlace toPlace(Feature feature) {
        if (feature == null || feature.geometry() == null || feature.properties() == null) {
            return null;
        }
        List<Double> coordinates = feature.geometry().coordinates();
        if (coordinates == null || coordinates.size() < 2) {
            return null;
        }
        Properties p = feature.properties();
        String street = join(" ", p.housenumber(), p.street());
        String city = firstNonBlank(p.city(), p.locality(), p.district(), p.county(), p.state());
        String name = firstNonBlank(p.name(), street, city);
        if (name == null) {
            return null;
        }
        String finalName = name;
        String address = Stream.of(street, p.district(), city, p.country())
                .filter(part -> part != null && !part.isBlank() && !part.equals(finalName))
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        String externalId = p.osmType() == null || p.osmId() == null
                ? null
                : "osm:" + p.osmType().toUpperCase(Locale.ROOT) + p.osmId();
        return new GeocodedPlace(name, address, city, coordinates.get(1), coordinates.get(0), p.osmValue(), externalId);
    }

    private static String join(String separator, String... parts) {
        String joined = String.join(separator, Stream.of(parts).filter(s -> s != null && !s.isBlank()).toList());
        return joined.isEmpty() ? null : joined;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // ----- Photon GeoJSON answer (only the fields we use) -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotonResponse(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Geometry geometry, Properties properties) {
    }

    /** GeoJSON order: [longitude, latitude]. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<Double> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(
            String name,
            String housenumber,
            String street,
            String district,
            String locality,
            String city,
            String county,
            String state,
            String country,
            @JsonProperty("osm_type") String osmType,
            @JsonProperty("osm_id") Long osmId,
            @JsonProperty("osm_key") String osmKey,
            @JsonProperty("osm_value") String osmValue
    ) {
    }
}

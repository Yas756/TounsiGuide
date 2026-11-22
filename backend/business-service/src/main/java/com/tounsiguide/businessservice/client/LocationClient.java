package com.tounsiguide.businessservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads the Location &amp; Matching Service (8082). The Business Service never creates or changes a Location:
 * a page is always linked to an existing one (S3-US03), and the wall is built from its CONFIRMED associations.
 */
@Component
public class LocationClient {

    /** Page size used to read the confirmed post ids (the Location Service's maximum). */
    static final int PAGE_SIZE = 100;
    /** Safety stop: 50 x 100 = 5,000 posts on one wall. */
    static final int MAX_PAGES = 50;

    private static final ParameterizedTypeReference<List<LocationSnapshot>> LOCATIONS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public LocationClient(
            @Value("${tounsiguide.location.url:http://localhost:8082}") String locationUrl,
            @Value("${tounsiguide.security.internal-token}") String internalToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(locationUrl).requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
    }

    /** @return the Location, or empty when the Location Service answers 404 */
    public Optional<LocationSnapshot> findLocation(long locationId) {
        try {
            return Optional.ofNullable(restClient.get().uri("/api/v1/locations/{id}", locationId)
                    .retrieve().body(LocationSnapshot.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    /** Several Locations in one call, keyed by id (unknown ids are absent). */
    public Map<Long, LocationSnapshot> findLocations(Collection<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        String ids = locationIds.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        try {
            List<LocationSnapshot> found = restClient.get().uri("/api/v1/locations?ids={ids}", ids)
                    .retrieve().body(LOCATIONS);
            Map<Long, LocationSnapshot> byId = new LinkedHashMap<>();
            if (found != null) {
                found.forEach(location -> byId.put(location.id(), location));
            }
            return byId;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * Ids of every post with a CONFIRMED association to the Location ({@code GET /api/v1/locations/{id}/posts}),
     * including posts written before the page existed. Posts deleted in the Community Service are left out by the
     * Location Service.
     */
    public List<Long> confirmedPostIds(long locationId) {
        List<Long> ids = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                IdPage result = restClient.get()
                        .uri("/api/v1/locations/{id}/posts?page={page}&size={size}", locationId, page, PAGE_SIZE)
                        .retrieve().body(IdPage.class);
                if (result == null || result.content() == null) {
                    break;
                }
                ids.addAll(result.content());
                if (result.content().isEmpty() || page + 1 >= result.totalPages()) {
                    break;
                }
            }
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
        return ids;
    }

    private static DependencyUnavailableException unavailable(Throwable cause) {
        return new DependencyUnavailableException("LOCATION_SERVICE_UNAVAILABLE",
                "Le service des lieux est temporairement indisponible", cause);
    }

    /** The fields of the Location Service's LocationResponse that this service uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationSnapshot(Long id, String name, String city, String address, String category) {
    }

    /** The Location Service's page of post ids. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdPage(List<Long> content, int page, int size, long totalElements, int totalPages) {
    }
}

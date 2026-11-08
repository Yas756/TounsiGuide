package com.tounsiguide.communityservice.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * REST client to the Location &amp; Matching Service (8082). The Community Service no longer stores
 * places: it only keeps a {@code locationId} and checks here that the Location exists.
 */
@Component
public class LocationClient {

    private final RestClient restClient;

    public LocationClient(
            @Value("${tounsiguide.services.location-url:http://localhost:8082}") String locationServiceUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(locationServiceUrl).requestFactory(requestFactory).build();
    }

    /**
     * Does nothing when {@code locationId} is null (a post without a place) or the Location exists.
     *
     * @throws UnknownLocationException            when the Location Service answers 404
     * @throws LocationServiceUnavailableException when it cannot be reached
     */
    public void requireExists(Long locationId) {
        if (locationId == null) {
            return;
        }
        try {
            restClient.get().uri("/api/v1/locations/{id}", locationId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new UnknownLocationException(locationId);
            }
            throw new LocationServiceUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new LocationServiceUnavailableException(exception);
        }
    }
}

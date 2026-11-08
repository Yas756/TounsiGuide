package com.tounsiguide.locationservice.geocoding;

import java.util.List;
import java.util.Optional;

/**
 * External place search. The browser never calls the provider itself: it goes through this service,
 * so the provider can be changed (Photon today, a commercial one later) without touching the frontend.
 */
public interface GeocodingProvider {

    /** Identifier used in {@code tounsiguide.geocoding.provider}. */
    String name();

    /**
     * Places matching the text, closest to the bias point first when one is given.
     *
     * @throws GeocodingUnavailableException when the provider cannot be reached
     */
    List<GeocodedPlace> search(String query, int limit, Double biasLatitude, Double biasLongitude);

    /**
     * The named place nearest to a point (a click on the map).
     *
     * @throws GeocodingUnavailableException when the provider cannot be reached
     */
    Optional<GeocodedPlace> reverse(double latitude, double longitude);
}

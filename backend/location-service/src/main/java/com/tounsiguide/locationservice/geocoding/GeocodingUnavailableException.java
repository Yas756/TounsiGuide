package com.tounsiguide.locationservice.geocoding;

/** The external geocoding provider did not answer (network, timeout, rate limit). */
public class GeocodingUnavailableException extends RuntimeException {

    public GeocodingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

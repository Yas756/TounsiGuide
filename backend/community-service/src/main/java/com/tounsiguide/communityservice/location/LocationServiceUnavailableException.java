package com.tounsiguide.communityservice.location;

/** The Location Service could not be reached to check a locationId (answered with 503). */
public class LocationServiceUnavailableException extends RuntimeException {

    public LocationServiceUnavailableException(Throwable cause) {
        super("Location service unavailable", cause);
    }
}

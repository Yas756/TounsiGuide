package com.tounsiguide.communityservice.location;

/** The post refers to a Location that does not exist in the Location Service (answered with 400). */
public class UnknownLocationException extends RuntimeException {

    public UnknownLocationException(Long locationId) {
        super("Location not found: " + locationId);
    }
}

package com.tounsiguide.locationservice.matching;

/** A place we may want to store: what we know about it before it becomes a Location. */
public record PlaceCandidate(
        String name,
        String city,
        Double latitude,
        Double longitude,
        String externalId
) {
}

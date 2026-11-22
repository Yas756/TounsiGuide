package com.tounsiguide.locationservice.location.dto;

import com.tounsiguide.locationservice.geocoding.GeocodedPlace;

/** A place found on the map (not stored yet). Sent back to POST /resolve when the member picks it. */
public record PlaceSuggestion(
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        String category,
        String externalId
) {

    public static PlaceSuggestion from(GeocodedPlace place) {
        return new PlaceSuggestion(place.name(), place.address(), place.city(), place.latitude(),
                place.longitude(), place.category(), place.externalId());
    }
}

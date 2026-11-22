package com.tounsiguide.locationservice.geocoding;

/**
 * A place found by the geocoding provider. It is not stored: it becomes a Location only when a
 * member picks it (POST /api/v1/locations/resolve).
 *
 * @param externalId id at the provider, e.g. "osm:N123456"
 * @param category   kind of place in OpenStreetMap vocabulary (cafe, restaurant, museum...)
 */
public record GeocodedPlace(
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        String category,
        String externalId
) {
}

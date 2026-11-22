package com.tounsiguide.locationservice.location.dto;

import java.util.List;

/**
 * Answer of GET /api/v1/locations/search.
 *
 * @param directory         Locations already known to TounsiGuide, best match first
 * @param external          places found on the map (OpenStreetMap), minus those already in {@code directory}
 * @param externalAvailable false when the geocoder could not be reached (the directory still answers)
 */
public record PlaceSearchResponse(
        List<LocationResponse> directory,
        List<PlaceSuggestion> external,
        boolean externalAvailable
) {
}

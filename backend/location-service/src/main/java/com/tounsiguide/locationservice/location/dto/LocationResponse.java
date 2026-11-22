package com.tounsiguide.locationservice.location.dto;

import com.tounsiguide.locationservice.location.LocationSource;

import java.time.Instant;
import java.util.List;

/** A Location as returned by the API. */
public record LocationResponse(
        Long id,
        String name,
        String city,
        String address,
        Double latitude,
        Double longitude,
        String category,
        LocationSource source,
        List<String> aliases,
        Instant createdAt
) {
}

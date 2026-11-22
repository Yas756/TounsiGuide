package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.location.dto.LocationResponse;
import org.springframework.stereotype.Component;

@Component
public class LocationMapper {

    public LocationResponse toResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getCity(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCategory(),
                location.getSource(),
                location.getAliases().stream().map(LocationAlias::getAlias).toList(),
                location.getCreatedAt()
        );
    }
}

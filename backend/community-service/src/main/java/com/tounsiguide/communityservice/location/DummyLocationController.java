package com.tounsiguide.communityservice.location;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dummy-locations")
public class DummyLocationController {

    private final DummyLocationService locationService;

    public DummyLocationController(DummyLocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<DummyLocationResponse> getAllLocations() {
        return locationService.getAllLocations().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{locationId}")
    public DummyLocationResponse getLocationById(@PathVariable Long locationId) {
        return toResponse(locationService.getLocationById(locationId));
    }

    private DummyLocationResponse toResponse(DummyLocation location) {
        return new DummyLocationResponse(
                location.getId(),
                location.getName(),
                location.getDescription(),
                location.getCity()
        );
    }
}

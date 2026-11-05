package com.tounsiguide.communityservice.location;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DummyLocationService {

    private final DummyLocationRepository locationRepository;

    public DummyLocationService(DummyLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<DummyLocation> getAllLocations() {
        return locationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DummyLocation getLocationById(Long locationId) {
        return findLocationOrThrow(locationId);
    }

    @Transactional(readOnly = true)
    public void validateLocationId(Long locationId) {
        if (locationId != null) {
            findLocationOrThrow(locationId);
        }
    }

    private DummyLocation findLocationOrThrow(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Dummy location not found: " + locationId
                ));
    }
}

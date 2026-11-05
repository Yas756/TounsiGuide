package com.tounsiguide.communityservice.location;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DummyLocationServiceTest {

    @Mock
    private DummyLocationRepository locationRepository;

    @InjectMocks
    private DummyLocationService locationService;

    @Test
    void getAllLocationsReturnsCatalogEntries() {
        DummyLocation café = new DummyLocation(
                "Café Example",
                "A demonstration café location.",
                "Tunis"
        );
        when(locationRepository.findAll()).thenReturn(List.of(café));

        List<DummyLocation> locations = locationService.getAllLocations();

        assertThat(locations).containsExactly(café);
        verify(locationRepository).findAll();
    }

    @Test
    void nullLocationIdIsAllowedForPosts() {
        locationService.validateLocationId(null);

        verify(locationRepository, org.mockito.Mockito.never()).findById(null);
    }

    @Test
    void existingLocationIdIsAccepted() {
        DummyLocation location = new DummyLocation(
                "Café Example",
                "A demonstration café location.",
                "Tunis"
        );
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        locationService.validateLocationId(1L);

        verify(locationRepository).findById(1L);
    }

    @Test
    void unknownLocationIdIsRejected() {
        when(locationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.validateLocationId(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Dummy location not found: 999");
    }
}

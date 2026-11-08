package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.common.error.ConflictException;
import com.tounsiguide.locationservice.common.security.CurrentUser;
import com.tounsiguide.locationservice.common.security.ForbiddenException;
import com.tounsiguide.locationservice.geocoding.GeocodedPlace;
import com.tounsiguide.locationservice.geocoding.GeocodingProvider;
import com.tounsiguide.locationservice.geocoding.GeocodingUnavailableException;
import com.tounsiguide.locationservice.location.dto.CreateLocationRequest;
import com.tounsiguide.locationservice.location.dto.PlaceSearchResponse;
import com.tounsiguide.locationservice.location.dto.ResolvePlaceRequest;
import com.tounsiguide.locationservice.matching.RuleBasedMatchingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    private static final CurrentUser MEMBER = new CurrentUser("u1", "Amira", Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("a1", "Admin", Set.of("MEMBER", "ADMIN"));

    @Mock
    private LocationRepository repository;

    @Mock
    private GeocodingProvider geocoder;

    private LocationService service;

    @BeforeEach
    void setUp() {
        service = new LocationService(repository, new LocationMapper(), new RuleBasedMatchingProvider(), geocoder);
    }

    private static Location withId(Location location, long id) {
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    private static CreateLocationRequest createRequest(String name, String city) {
        return new CreateLocationRequest(name, city, null, 36.8, 10.18, "cafe", List.of("Alias"));
    }

    @Test
    void adminCreatesDirectoryLocationWithAliases() {
        when(repository.existsByNormalizedNameAndNormalizedCity("cafe central", "tunis")).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(call -> withId(call.getArgument(0), 10L));

        var response = service.create(createRequest("Café Central", "Tunis"), ADMIN);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.source()).isEqualTo(LocationSource.DIRECTORY);
        assertThat(response.aliases()).containsExactly("Alias");
    }

    @Test
    void duplicateNameInSameCityIsRejectedWith409() {
        when(repository.existsByNormalizedNameAndNormalizedCity("cafe central", "tunis")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("CAFÉ  central", "tunis"), ADMIN))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void memberCannotChangeTheDirectory() {
        assertThatThrownBy(() -> service.create(createRequest("Café Central", "Tunis"), MEMBER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resolveReturnsExistingLocationWithSameExternalId() {
        Location existing = withId(new Location("Café des Délices", "Sidi Bou Saïd", null, 36.87, 10.349,
                "cafe", LocationSource.MAP, "osm:N42", "u2"), 5L);
        when(repository.findFirstByExternalId("osm:N42")).thenReturn(Optional.of(existing));

        var result = service.resolve(new ResolvePlaceRequest("Café des Délices", null, null, 36.87, 10.349,
                "cafe", "osm:N42"), MEMBER);

        assertThat(result.created()).isFalse();
        assertThat(result.location().id()).isEqualTo(5L);
        verify(repository, never()).save(any());
    }

    @Test
    void resolveReusesSameNameNearbyAndCreatesOtherwise() {
        Location nearby = withId(new Location("Café Example", "Tunis", null, 36.8003, 10.1857, "cafe",
                LocationSource.DIRECTORY, null, "seed"), 1L);
        when(repository.findByNormalizedName("cafe example")).thenReturn(List.of(nearby));
        when(repository.save(any(Location.class))).thenAnswer(call -> withId(call.getArgument(0), 99L));

        var same = service.resolve(new ResolvePlaceRequest("Cafe Example", null, null, 36.8004, 10.1858, null, null), MEMBER);
        var other = service.resolve(new ResolvePlaceRequest("Café Example", "Sousse", null, 35.82, 10.63, null, null), MEMBER);

        assertThat(same.created()).isFalse();
        assertThat(same.location().id()).isEqualTo(1L);
        assertThat(other.created()).isTrue();
        assertThat(other.location().id()).isEqualTo(99L);
        assertThat(other.location().source()).isEqualTo(LocationSource.MAP);
    }

    @Test
    void searchPutsDirectoryFirstAndHidesExternalDuplicates() {
        Location bardo = withId(new Location("Musée national du Bardo", "Tunis", null, 36.8095, 10.1347,
                "museum", LocationSource.DIRECTORY, null, "seed"), 4L);
        bardo.addAlias("Bardo");
        when(repository.findCandidates(eq("bardo"), any())).thenReturn(List.of(bardo));
        when(geocoder.search(eq("bardo"), anyInt(), any(), any())).thenReturn(List.of(
                new GeocodedPlace("Musée national du Bardo", null, "Tunis", 36.8096, 10.1348, "museum", "osm:W9"),
                new GeocodedPlace("Le Bardo", null, "Le Bardo", 36.8090, 10.1400, "suburb", "osm:R3")));

        PlaceSearchResponse response = service.searchPlaces("bardo", 8, null, null);

        assertThat(response.directory()).extracting("id").containsExactly(4L);
        assertThat(response.external()).extracting("externalId").containsExactly("osm:R3");
        assertThat(response.externalAvailable()).isTrue();
    }

    @Test
    void searchStillAnswersFromDirectoryWhenGeocoderIsDown() {
        when(repository.findCandidates(anyString(), any())).thenReturn(List.of());
        when(geocoder.search(anyString(), anyInt(), any(), any()))
                .thenThrow(new GeocodingUnavailableException("down", null));

        PlaceSearchResponse response = service.searchPlaces("plage", 8, null, null);

        assertThat(response.externalAvailable()).isFalse();
        assertThat(response.external()).isEmpty();
    }

    @Test
    void shortQueriesAreNotSentToTheGeocoder() {
        when(repository.findCandidates(anyString(), any())).thenReturn(List.of());

        service.searchPlaces("ca", 8, null, null);

        verify(geocoder, never()).search(anyString(), anyInt(), any(), any());
    }
}

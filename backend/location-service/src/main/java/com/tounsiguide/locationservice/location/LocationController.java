package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.common.security.CurrentUser;
import com.tounsiguide.locationservice.location.dto.AddAliasRequest;
import com.tounsiguide.locationservice.location.dto.CreateLocationRequest;
import com.tounsiguide.locationservice.location.dto.LocationResponse;
import com.tounsiguide.locationservice.location.dto.PlaceSearchResponse;
import com.tounsiguide.locationservice.location.dto.PlaceSuggestion;
import com.tounsiguide.locationservice.location.dto.ResolvePlaceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public reads, member resolve, admin writes. The Gateway has already checked the token. */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * {@code ?query=bardo} searches the directory by name, alias or city (S2-B05).
     * {@code ?ids=1,2,3} returns those Locations (used by the feed). Neither: first Locations by name.
     */
    @GetMapping
    public List<LocationResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (ids != null) {
            return locationService.getByIds(ids);
        }
        return locationService.searchDirectory(query, limit);
    }

    @GetMapping("/{locationId}")
    public LocationResponse get(@PathVariable Long locationId) {
        return locationService.getById(locationId);
    }

    /** Place picker search: directory first, then places from the map provider. */
    @GetMapping("/search")
    public PlaceSearchResponse search(
            @RequestParam String query,
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return locationService.searchPlaces(query, limit, lat, lng);
    }

    /** Name of the place under a map click; 204 when nothing named is nearby. */
    @GetMapping("/reverse")
    public ResponseEntity<PlaceSuggestion> reverse(@RequestParam double lat, @RequestParam double lng) {
        return locationService.reverse(lat, lng)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** A member picked a place: 200 with the existing Location, or 201 with the new one. */
    @PostMapping("/resolve")
    public ResponseEntity<LocationResponse> resolve(
            @Valid @RequestBody ResolvePlaceRequest request,
            CurrentUser user
    ) {
        LocationService.ResolveResult result = locationService.resolve(request, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.location());
    }

    /** Administrator adds a Location to the directory (S2-US10). */
    @PostMapping
    public ResponseEntity<LocationResponse> create(
            @Valid @RequestBody CreateLocationRequest request,
            CurrentUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.create(request, user));
    }

    @PostMapping("/{locationId}/aliases")
    public LocationResponse addAlias(
            @PathVariable Long locationId,
            @Valid @RequestBody AddAliasRequest request,
            CurrentUser user
    ) {
        return locationService.addAlias(locationId, request.alias(), user);
    }
}

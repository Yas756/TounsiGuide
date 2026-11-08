package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.locationservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.locationservice.location.dto.LocationResponse;
import com.tounsiguide.locationservice.location.dto.PlaceSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    private static final String RESOLVE_BODY = """
            {"name":"Café des Délices","latitude":36.87,"longitude":10.349,"externalId":"osm:N42"}
            """;

    @Mock
    private LocationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LocationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private static LocationResponse response(long id) {
        return new LocationResponse(id, "Café des Délices", "Sidi Bou Saïd", null, 36.87, 10.349, "cafe",
                LocationSource.MAP, List.of(), Instant.now());
    }

    @Test
    void idsParameterReturnsThoseLocations() throws Exception {
        when(service.getByIds(List.of(1L, 2L))).thenReturn(List.of(response(1), response(2)));

        mockMvc.perform(get("/api/v1/locations").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchReturnsDirectoryAndExternalLists() throws Exception {
        when(service.searchPlaces("cafe", 8, null, null))
                .thenReturn(new PlaceSearchResponse(List.of(response(1)), List.of(), true));

        mockMvc.perform(get("/api/v1/locations/search").param("query", "cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directory[0].id").value(1))
                .andExpect(jsonPath("$.externalAvailable").value(true));
    }

    @Test
    void reverseWithNothingNearbyIsNoContent() throws Exception {
        when(service.reverse(36.0, 10.0)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/locations/reverse").param("lat", "36").param("lng", "10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void resolveWithoutUserIs401() throws Exception {
        mockMvc.perform(post("/api/v1/locations/resolve").contentType(MediaType.APPLICATION_JSON).content(RESOLVE_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void resolveCreatingALocationIs201() throws Exception {
        when(service.resolve(any(), any())).thenReturn(new LocationService.ResolveResult(response(7), true));

        mockMvc.perform(post("/api/v1/locations/resolve")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESOLVE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void resolveWithoutCoordinatesIs400() throws Exception {
        mockMvc.perform(post("/api/v1/locations/resolve")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Somewhere\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

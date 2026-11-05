package com.tounsiguide.communityservice.location;

import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DummyLocationControllerTest {

    @Mock
    private DummyLocationService locationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyLocationController(locationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllLocationsReturnsOk() throws Exception {
        when(locationService.getAllLocations()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dummy-locations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getLocationByIdReturnsOk() throws Exception {
        DummyLocation location = new DummyLocation(
                "Café Example",
                "A demonstration café location.",
                "Tunis"
        );
        when(locationService.getLocationById(1L)).thenReturn(location);

        mockMvc.perform(get("/api/v1/dummy-locations/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

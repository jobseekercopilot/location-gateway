package com.jobseekercopilot.locationgateway;

import com.jobseekercopilot.locationgateway.controller.LocationController;
import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
class LocationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @Test
    void testGetLocationByPostcode_success() throws Exception {

        Location mockLocation = new Location(
                "LS1",
                "Leeds, Yorkshire and the Humber",
                "LS1",
                "Yorkshire and the Humber",
                53.8008,
                -1.5491
        );

        when(locationService.getLocationFromPostcodeIo("LS1"))
                .thenReturn(mockLocation);

        mockMvc.perform(get("/api/postcodes/LS1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Retrieved location for postcode LS1."))
                .andExpect(jsonPath("$.locations", hasSize(1)))
                .andExpect(jsonPath("$.locations[0].id")
                        .value("LS1"));
    }

    @Test
    void testGetLocationByPostcode_error() throws Exception {

        when(locationService.getLocationFromPostcodeIo("INVALID"))
                .thenThrow(new RuntimeException("Postcode not found"));

        mockMvc.perform(get("/api/postcodes/INVALID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Error retrieving location for postcode INVALID: Postcode not found"));
    }
}

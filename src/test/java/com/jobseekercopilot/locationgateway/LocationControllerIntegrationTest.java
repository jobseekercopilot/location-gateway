package com.jobseekercopilot.locationgateway;

import com.jobseekercopilot.locationgateway.controller.LocationController;
import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
public class LocationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocationService locationService;

    @Test
    public void testSearchLocations_success() throws Exception {
        Location mockLoc = new Location("loc-1", "Leeds, West Yorkshire", "LS1", "Yorkshire and the Humber");
        when(locationService.searchLocations("Leeds")).thenReturn(List.of(mockLoc));

        mockMvc.perform(get("/api/locations")
                .param("q", "Leeds")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Retrieved 1 matching UK location."))
                .andExpect(jsonPath("$.locations", hasSize(1)))
                .andExpect(jsonPath("$.locations[0].id").value("loc-1"))
                .andExpect(jsonPath("$.locations[0].name").value("Leeds, West Yorkshire"));
    }

    @Test
    public void testSearchLocations_missingQuery() throws Exception {
        mockMvc.perform(get("/api/locations")
                .param("q", "")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing search query parameter. 'q' must be provided."));
    }

    @Test
    public void testGetLocationByPostcode_success() throws Exception {
        Location mockLoc = new Location("external-LS1", "Leeds, Yorkshire and the Humber", "LS1", "Yorkshire and the Humber");
        when(locationService.getLocationFromPostcodeIo("LS1")).thenReturn(Mono.just(mockLoc));

        MvcResult mvcResult = mockMvc.perform(get("/api/postcodes/LS1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Retrieved location for postcode LS1."))
                .andExpect(jsonPath("$.locations", hasSize(1)))
                .andExpect(jsonPath("$.locations[0].id").value("external-LS1"));
    }

    @Test
    public void testGetLocationByPostcode_error() throws Exception {
        when(locationService.getLocationFromPostcodeIo("INVALID")).thenReturn(Mono.error(new RuntimeException("Postcode not found")));

        MvcResult mvcResult = mockMvc.perform(get("/api/postcodes/INVALID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error retrieving location for postcode INVALID: Postcode not found"));
    }
}

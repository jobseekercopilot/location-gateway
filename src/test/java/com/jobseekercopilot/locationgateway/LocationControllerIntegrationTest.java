package com.jobseekercopilot.locationgateway;

import com.jobseekercopilot.locationgateway.controller.LocationController;
import com.jobseekercopilot.locationgateway.exception.InvalidPostcodeException;
import com.jobseekercopilot.locationgateway.exception.LocationLookupException;
import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.ratelimit.CallerRateLimiter;
import com.jobseekercopilot.locationgateway.ratelimit.LocationRateLimitException;
import com.jobseekercopilot.locationgateway.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
class LocationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private CallerRateLimiter callerRateLimiter;

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
                        .value("Location retrieved."))
                .andExpect(jsonPath("$.locations", hasSize(1)))
                .andExpect(jsonPath("$.locations[0].id")
                        .value("LS1"));
    }

    @Test
    void invalidPostcodeReturnsStableBadRequestWithoutEchoingInput() throws Exception {

        when(locationService.getLocationFromPostcodeIo("INVALID"))
                .thenThrow(new InvalidPostcodeException());

        mockMvc.perform(get("/api/postcodes/INVALID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid postcode or outcode."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("INVALID"))))
                .andExpect(jsonPath("$.locations", hasSize(0)));
    }

    @Test
    void providerStatusIsReturnedWithStableMessageWithoutLeakingCause() throws Exception {
        when(locationService.getLocationFromPostcodeIo("SW1A1AA"))
                .thenThrow(new LocationLookupException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        new RestClientException("secret downstream body for SW1A1AA")
                ));

        mockMvc.perform(get("/api/postcodes/SW1A1AA")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.statusCode").value(503))
                .andExpect(jsonPath("$.message").value("Location service is temporarily unavailable."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret downstream body"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SW1A1AA"))));
    }

    @Test
    void unacceptableResponseContentTypeReturnsStableJsonError() throws Exception {
        mockMvc.perform(get("/api/postcodes/LS1")
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));
    }

    @Test
    void callerRateLimitReturnsStableRetryableResponseWithoutCallingService() throws Exception {
        doThrow(new LocationRateLimitException(42))
                .when(callerRateLimiter).check(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(get("/api/postcodes/LS1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.statusCode").value(429))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many location requests. Try again later."))
                .andExpect(jsonPath("$.locations", hasSize(0)));

        verifyNoInteractions(locationService);
    }
}

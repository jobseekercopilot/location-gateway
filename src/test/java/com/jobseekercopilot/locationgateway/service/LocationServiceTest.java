package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.locationgateway.client.PostcodeIoClient;
import com.jobseekercopilot.locationgateway.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LocationServiceTest {

    @Mock
    private PostcodeIoClient postcodeIoClient;

    @InjectMocks
    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService.init();
    }

    @Test
    void testSearchLocations_success() {
        List<Location> results = locationService.searchLocations("Leeds");
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("loc-1", results.get(0).getId());
        assertEquals("Leeds, West Yorkshire", results.get(0).getName());
    }

    @Test
    void testSearchLocations_emptyQuery() {
        List<Location> results = locationService.searchLocations("");
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchLocations_nullQuery() {
        List<Location> results = locationService.searchLocations(null);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetLocationFromPostcodeIo_success() {
        String postcode = "LS1";
        PostcodeIoClient.PostcodeIoLocation mockLocation = new PostcodeIoClient.PostcodeIoLocation();
        mockLocation.setPostcode(postcode);
        mockLocation.setRegion("Yorkshire and the Humber");
        mockLocation.setAdminDistrict("Leeds");

        when(postcodeIoClient.getPostcodeDetails(postcode)).thenReturn(Mono.just(mockLocation));

        Mono<Location> resultMono = locationService.getLocationFromPostcodeIo(postcode);
        Location location = resultMono.block();

        assertNotNull(location);
        assertEquals("external-LS1", location.getId());
        assertEquals("Leeds, Yorkshire and the Humber", location.getName());
        assertEquals("LS1", location.getPostcode());
        assertEquals("Yorkshire and the Humber", location.getRegion());

        verify(postcodeIoClient, times(1)).getPostcodeDetails(postcode);
    }
}

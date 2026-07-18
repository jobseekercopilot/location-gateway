package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.locationgateway.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private PostcodeApi postcodeApi;

    @InjectMocks
    private LocationService locationService;

    @Test
    void testGetLocationFromPostcodeIo_success() {
        String postcode = "LS1";

        PostcodeLocation mockLocation = new PostcodeLocation();
        mockLocation.setPostcode(postcode);
        mockLocation.setRegion("Yorkshire and the Humber");
        mockLocation.setAdminDistrict("Leeds");
        mockLocation.setLatitude(53.8008);
        mockLocation.setLongitude(-1.5491);

        when(postcodeApi.getLocationByPostcode(postcode)).thenReturn(mockLocation);

        Location location = locationService.getLocationFromPostcodeIo(postcode);

        assertNotNull(location);
        assertEquals("LS1", location.getId());
        assertEquals("Leeds, Yorkshire and the Humber", location.getName());
        assertEquals("LS1", location.getPostcode());
        assertEquals("Yorkshire and the Humber", location.getRegion());
        assertEquals(53.8008, location.getLatitude());
        assertEquals(-1.5491, location.getLongitude());

        verify(postcodeApi, times(1)).getLocationByPostcode(postcode);
    }
}

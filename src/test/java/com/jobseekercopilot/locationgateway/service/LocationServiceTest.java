package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.exception.InvalidPostcodeException;
import com.jobseekercopilot.locationgateway.exception.LocationLookupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.stream.Stream;

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
        String postcode = " ls1 ";

        PostcodeLocation mockLocation = new PostcodeLocation();
        mockLocation.setPostcode("LS1");
        mockLocation.setRegion("Yorkshire and the Humber");
        mockLocation.setAdminDistrict("Leeds");
        mockLocation.setLatitude(53.8008);
        mockLocation.setLongitude(-1.5491);

        when(postcodeApi.getLocationByPostcode("LS1")).thenReturn(mockLocation);

        Location location = locationService.getLocationFromPostcodeIo(postcode);

        assertNotNull(location);
        assertEquals("LS1", location.getId());
        assertEquals("Leeds, Yorkshire and the Humber", location.getName());
        assertEquals("LS1", location.getPostcode());
        assertEquals("Yorkshire and the Humber", location.getRegion());
        assertEquals(53.8008, location.getLatitude());
        assertEquals(-1.5491, location.getLongitude());

        verify(postcodeApi, times(1)).getLocationByPostcode("LS1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "INVALID", "123", "SW1A 1A!", "AAAAAAAA"})
    void rejectsInvalidPostcodesBeforeCallingProvider(String postcode) {
        assertThrows(InvalidPostcodeException.class,
                () -> locationService.getLocationFromPostcodeIo(postcode));
        verifyNoInteractions(postcodeApi);
    }

    @ParameterizedTest
    @MethodSource("providerStatuses")
    void mapsProviderStatusesToStableGatewayStatuses(HttpStatus providerStatus, HttpStatus expectedStatus) {
        RestClientException providerFailure = providerStatus.is4xxClientError()
                ? new HttpClientErrorException(providerStatus, "sensitive provider detail")
                : new HttpServerErrorException(providerStatus, "sensitive provider detail");
        when(postcodeApi.getLocationByPostcode("SW1A1AA")).thenThrow(providerFailure);

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("sw1a 1aa"));

        assertEquals(expectedStatus, exception.getStatus());
        assertSame(providerFailure, exception.getCause());
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        when(postcodeApi.getLocationByPostcode("LS1"))
                .thenThrow(new ResourceAccessException("connection refused"));

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void mapsOtherClientFailureToBadGateway() {
        when(postcodeApi.getLocationByPostcode("LS1"))
                .thenThrow(new RestClientException("invalid response"));

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    private static Stream<Arguments> providerStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(HttpStatus.REQUEST_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY),
                Arguments.of(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT)
        );
    }
}

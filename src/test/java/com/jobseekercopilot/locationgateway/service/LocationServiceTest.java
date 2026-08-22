package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.generated.postcodeiogateway.model.PlaceLocation;
import com.jobseekercopilot.locationgateway.client.PostcodeGatewayCircuitOpenException;
import com.jobseekercopilot.locationgateway.client.ResilientPostcodeGatewayClient;
import com.jobseekercopilot.locationgateway.cache.LocationLookupCache;
import com.jobseekercopilot.locationgateway.exception.InvalidPostcodeException;
import com.jobseekercopilot.locationgateway.exception.LocationLookupException;
import com.jobseekercopilot.locationgateway.model.Location;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.stream.Stream;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private ResilientPostcodeGatewayClient postcodeGatewayClient;

    @Mock
    private LocationLookupCache locationLookupCache;

    @InjectMocks
    private LocationService locationService;

    @Test
    void mapsBoundedCanonicalPlaceSearchWithoutCachingQueryState() {
        PlaceLocation place = new PlaceLocation();
        place.setId("place-1");
        place.setName("St Albans");
        place.setPostcode("AL1");
        place.setRegion("East of England");
        place.setLatitude(51.7527);
        place.setLongitude(-0.3394);
        when(postcodeGatewayClient.searchPlaces("St Albans", 10)).thenReturn(java.util.List.of(place));

        java.util.List<Location> locations = locationService.searchLocations("  St   Albans ");

        assertEquals(1, locations.size());
        assertEquals("place-1", locations.get(0).getId());
        assertEquals("AL1", locations.get(0).getPostcode());
        verify(postcodeGatewayClient).searchPlaces("St Albans", 10);
        verifyNoInteractions(locationLookupCache);
    }

    @Test
    void mapsPlaceProviderFailuresWithoutLeakingQuery() {
        when(postcodeGatewayClient.searchPlaces("Secret Place", 10))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        LocationLookupException exception = assertThrows(
                LocationLookupException.class,
                () -> locationService.searchLocations("Secret Place"));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertFalse(exception.getMessage().contains("Secret Place"));
    }

    @Test
    void testGetLocationFromPostcodeIo_success() {
        String postcode = " ls1 ";

        PostcodeLocation mockLocation = new PostcodeLocation();
        mockLocation.setPostcode("LS1");
        mockLocation.setRegion("Yorkshire and the Humber");
        mockLocation.setAdminDistrict("Leeds");
        mockLocation.setLatitude(53.8008);
        mockLocation.setLongitude(-1.5491);

        when(postcodeGatewayClient.lookup("LS1")).thenReturn(mockLocation);

        Location location = locationService.getLocationFromPostcodeIo(postcode);

        assertNotNull(location);
        assertEquals("LS1", location.getId());
        assertEquals("Leeds, Yorkshire and the Humber", location.getName());
        assertEquals("LS1", location.getPostcode());
        assertEquals("Yorkshire and the Humber", location.getRegion());
        assertEquals(53.8008, location.getLatitude());
        assertEquals(-1.5491, location.getLongitude());

        verify(postcodeGatewayClient, times(1)).lookup("LS1");
        verify(locationLookupCache).put(eq("LS1"), same(location));
    }

    @Test
    void canonicalPostcodeVariantsShareSuccessfulCacheEntry() {
        PostcodeLocation providerLocation = new PostcodeLocation();
        providerLocation.setPostcode("SW1A 1AA");
        providerLocation.setRegion("London");
        providerLocation.setAdminDistrict("Westminster");
        Location cached = new Location(
                "SW1A 1AA", "Westminster, London", "SW1A 1AA", "London", 51.501, -0.142);
        when(locationLookupCache.get("SW1A1AA"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cached));
        when(postcodeGatewayClient.lookup("SW1A1AA")).thenReturn(providerLocation);

        Location first = locationService.getLocationFromPostcodeIo("sw1a 1aa");
        Location second = locationService.getLocationFromPostcodeIo(" SW1A1AA ");

        assertEquals("SW1A 1AA", first.getPostcode());
        assertSame(cached, second);
        verify(postcodeGatewayClient).lookup("SW1A1AA");
        verify(locationLookupCache).put("SW1A1AA", first);
    }

    @Test
    void providerFailuresAreNeverCached() {
        when(locationLookupCache.get("LS1")).thenReturn(Optional.empty());
        when(postcodeGatewayClient.lookup("LS1"))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));
        assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        verify(postcodeGatewayClient, times(2)).lookup("LS1");
        verify(locationLookupCache, never()).put(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "INVALID", "123", "SW1A 1A!", "AAAAAAAA"})
    void rejectsInvalidPostcodesBeforeCallingProvider(String postcode) {
        assertThrows(InvalidPostcodeException.class,
                () -> locationService.getLocationFromPostcodeIo(postcode));
        verifyNoInteractions(postcodeGatewayClient);
    }

    @ParameterizedTest
    @MethodSource("providerStatuses")
    void mapsProviderStatusesToStableGatewayStatuses(HttpStatus providerStatus, HttpStatus expectedStatus) {
        RestClientException providerFailure = providerStatus.is4xxClientError()
                ? new HttpClientErrorException(providerStatus, "sensitive provider detail")
                : new HttpServerErrorException(providerStatus, "sensitive provider detail");
        when(postcodeGatewayClient.lookup("SW1A1AA")).thenThrow(providerFailure);

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("sw1a 1aa"));

        assertEquals(expectedStatus, exception.getStatus());
        assertSame(providerFailure, exception.getCause());
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        when(postcodeGatewayClient.lookup("LS1"))
                .thenThrow(new ResourceAccessException("connection refused"));

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void mapsReadTimeoutToGatewayTimeout() {
        when(postcodeGatewayClient.lookup("LS1"))
                .thenThrow(new ResourceAccessException("read timed out", new SocketTimeoutException()));

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void mapsOpenCircuitToServiceUnavailableWithoutProviderDetail() {
        when(postcodeGatewayClient.lookup("LS1"))
                .thenThrow(new PostcodeGatewayCircuitOpenException());

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void mapsOtherClientFailureToBadGateway() {
        when(postcodeGatewayClient.lookup("LS1"))
                .thenThrow(new RestClientException("invalid response"));

        LocationLookupException exception = assertThrows(LocationLookupException.class,
                () -> locationService.getLocationFromPostcodeIo("LS1"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    private static Stream<Arguments> providerStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(HttpStatus.REQUEST_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY),
                Arguments.of(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT)
        );
    }
}

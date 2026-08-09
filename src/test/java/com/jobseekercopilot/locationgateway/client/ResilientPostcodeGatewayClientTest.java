package com.jobseekercopilot.locationgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.generated.postcodeiogateway.model.PlaceLocation;
import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class ResilientPostcodeGatewayClientTest {

    @Test
    void searchesPlacesThroughTheSameBoundedResilienceAndTelemetryPath() {
        PostcodeApi api = mock(PostcodeApi.class);
        var expected = new com.jobseekercopilot.generated.postcodeiogateway.model.PlaceLocation();
        expected.setId("place-1");
        expected.setName("Leeds");
        expected.setPostcode("LS1");
        expected.setRegion("Yorkshire and The Humber");
        expected.setLatitude(53.8008);
        expected.setLongitude(-1.5491);
        when(api.searchPlaces("Leeds", 10))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(List.of(expected));
        PostcodeGatewayProperties properties = properties();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        List<Duration> sleeps = new ArrayList<>();
        ResilientPostcodeGatewayClient client = new ResilientPostcodeGatewayClient(
                api,
                new PostcodeGatewayCircuitBreaker(properties, new AtomicLong()::get),
                properties,
                metrics,
                sleeps::add);

        assertEquals(List.of(expected), client.searchPlaces("Leeds", 10));
        verify(api, times(2)).searchPlaces("Leeds", 10);
        assertEquals(List.of(Duration.ofMillis(10)), sleeps);
        assertEquals(1, metrics.get("location.postcode.provider.retries").counter().count());
    }

    @Test
    void rejectsMalformedOrExcessivePlaceResponses() {
        PostcodeApi api = mock(PostcodeApi.class);
        PlaceLocation incomplete = new PlaceLocation();
        incomplete.setId("place-1");
        when(api.searchPlaces("Leeds", 10)).thenReturn(List.of(incomplete));
        ResilientPostcodeGatewayClient client = client(
                api, properties(), new AtomicLong(), duration -> { });

        assertThrows(RestClientException.class, () -> client.searchPlaces("Leeds", 10));
        verify(api).searchPlaces("Leeds", 10);

        List<PlaceLocation> excessive = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            PlaceLocation place = new PlaceLocation();
            place.setId("place-" + index);
            place.setName("Leeds");
            place.setPostcode("LS1");
            place.setRegion("Yorkshire and The Humber");
            place.setLatitude(53.8);
            place.setLongitude(-1.5);
            excessive.add(place);
        }
        when(api.searchPlaces("York", 10)).thenReturn(excessive);

        assertThrows(RestClientException.class, () -> client.searchPlaces("York", 10));
        verify(api).searchPlaces("York", 10);
    }

    @Test
    void retriesOneSafeTransientFailureWithBoundedBackoff() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        PostcodeLocation expected = new PostcodeLocation();
        when(api.getLocationByPostcode("LS1"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(expected);
        PostcodeGatewayProperties properties = properties();
        List<Duration> sleeps = new ArrayList<>();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        ResilientPostcodeGatewayClient client = new ResilientPostcodeGatewayClient(
                api,
                new PostcodeGatewayCircuitBreaker(properties, new AtomicLong()::get),
                properties,
                metrics,
                sleeps::add);

        assertSame(expected, client.lookup("LS1"));
        verify(api, times(2)).getLocationByPostcode("LS1");
        assertEquals(List.of(Duration.ofMillis(10)), sleeps);
        assertEquals(1, metrics.get("location.postcode.provider.requests")
                .tag("outcome", "server_error").timer().count());
        assertEquals(1, metrics.get("location.postcode.provider.requests")
                .tag("outcome", "success").timer().count());
        assertEquals(1, metrics.get("location.postcode.provider.retries").counter().count());
        assertEquals(0, metrics.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .filter(tag -> tag.getValue().contains("LS1"))
                .count());
    }

    @Test
    void doesNotRetryCallerErrors() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        HttpClientErrorException notFound = new HttpClientErrorException(HttpStatus.NOT_FOUND);
        when(api.getLocationByPostcode("LS1")).thenThrow(notFound);
        List<Duration> sleeps = new ArrayList<>();
        ResilientPostcodeGatewayClient client = client(api, properties(), new AtomicLong(), sleeps::add);

        assertSame(notFound, assertThrows(HttpClientErrorException.class, () -> client.lookup("LS1")));
        verify(api).getLocationByPostcode("LS1");
        assertEquals(List.of(), sleeps);
    }

    @Test
    void doesNotMultiplyBlockingTransportTimeouts() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        ResourceAccessException timeout = new ResourceAccessException("read timed out");
        when(api.getLocationByPostcode("LS1")).thenThrow(timeout);
        List<Duration> sleeps = new ArrayList<>();
        ResilientPostcodeGatewayClient client = client(api, properties(), new AtomicLong(), sleeps::add);

        assertSame(timeout, assertThrows(ResourceAccessException.class, () -> client.lookup("LS1")));
        verify(api).getLocationByPostcode("LS1");
        assertEquals(List.of(), sleeps);
    }

    @Test
    void opensAfterFailedLogicalCallsAndRejectsWithoutUsingARequestThread() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        when(api.getLocationByPostcode("LS1"))
                .thenThrow(new ResourceAccessException("connection refused"));
        PostcodeGatewayProperties properties = properties();
        properties.setMaxAttempts(1);
        properties.setCircuitFailureThreshold(2);
        AtomicLong nanoTime = new AtomicLong();
        ResilientPostcodeGatewayClient client = client(api, properties, nanoTime, duration -> { });

        assertThrows(ResourceAccessException.class, () -> client.lookup("LS1"));
        assertThrows(ResourceAccessException.class, () -> client.lookup("LS1"));
        assertThrows(PostcodeGatewayCircuitOpenException.class, () -> client.lookup("LS1"));
        verify(api, times(2)).getLocationByPostcode("LS1");
    }

    @Test
    void permitsOneHalfOpenProbeAndClosesAfterRecovery() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        PostcodeLocation expected = new PostcodeLocation();
        when(api.getLocationByPostcode("LS1"))
                .thenThrow(new ResourceAccessException("outage"))
                .thenReturn(expected);
        PostcodeGatewayProperties properties = properties();
        properties.setMaxAttempts(1);
        properties.setCircuitFailureThreshold(1);
        properties.setCircuitOpenDuration(Duration.ofMillis(1));
        AtomicLong nanoTime = new AtomicLong();
        PostcodeGatewayCircuitBreaker breaker = new PostcodeGatewayCircuitBreaker(properties, nanoTime::get);
        ResilientPostcodeGatewayClient client = new ResilientPostcodeGatewayClient(
                api, breaker, properties, new SimpleMeterRegistry(), duration -> { });

        assertThrows(ResourceAccessException.class, () -> client.lookup("LS1"));
        assertThrows(PostcodeGatewayCircuitOpenException.class, () -> client.lookup("LS1"));
        nanoTime.addAndGet(Duration.ofMillis(1).toNanos() + 1);
        assertSame(expected, client.lookup("LS1"));
        assertEquals("CLOSED", breaker.stateName());
    }

    private static ResilientPostcodeGatewayClient client(
            PostcodeApi api,
            PostcodeGatewayProperties properties,
            AtomicLong nanoTime,
            ResilientPostcodeGatewayClient.Sleeper sleeper) {
        return new ResilientPostcodeGatewayClient(
                api,
                new PostcodeGatewayCircuitBreaker(properties, nanoTime::get),
                properties,
                new SimpleMeterRegistry(),
                sleeper);
    }

    private static PostcodeGatewayProperties properties() {
        PostcodeGatewayProperties properties = new PostcodeGatewayProperties();
        properties.setServiceToken("test-only-location-service-token-32-bytes");
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofMillis(10));
        properties.setMaxBackoff(Duration.ofMillis(20));
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenDuration(Duration.ofSeconds(1));
        return properties;
    }
}

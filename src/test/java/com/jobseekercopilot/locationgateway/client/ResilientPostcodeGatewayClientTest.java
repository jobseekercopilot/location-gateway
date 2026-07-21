package com.jobseekercopilot.locationgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class ResilientPostcodeGatewayClientTest {

    @Test
    void retriesOneSafeTransientFailureWithBoundedBackoff() {
        PostcodeApi api = org.mockito.Mockito.mock(PostcodeApi.class);
        PostcodeLocation expected = new PostcodeLocation();
        when(api.getLocationByPostcode("LS1"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(expected);
        PostcodeGatewayProperties properties = properties();
        List<Duration> sleeps = new ArrayList<>();
        ResilientPostcodeGatewayClient client = client(api, properties, new AtomicLong(), sleeps::add);

        assertSame(expected, client.lookup("LS1"));
        verify(api, times(2)).getLocationByPostcode("LS1");
        assertEquals(List.of(Duration.ofMillis(10)), sleeps);
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
                api, breaker, properties, duration -> { });

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
                sleeper);
    }

    private static PostcodeGatewayProperties properties() {
        PostcodeGatewayProperties properties = new PostcodeGatewayProperties();
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofMillis(10));
        properties.setMaxBackoff(Duration.ofMillis(20));
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenDuration(Duration.ofSeconds(1));
        return properties;
    }
}

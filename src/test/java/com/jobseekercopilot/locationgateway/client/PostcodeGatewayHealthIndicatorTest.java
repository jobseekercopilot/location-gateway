package com.jobseekercopilot.locationgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class PostcodeGatewayHealthIndicatorTest {

    @Test
    void readinessReflectsTheCircuitWithoutCallingTheProvider() {
        PostcodeGatewayProperties properties = new PostcodeGatewayProperties();
        properties.setServiceToken("test-only-location-service-token-32-bytes");
        properties.setCircuitFailureThreshold(1);
        properties.setCircuitOpenDuration(Duration.ofSeconds(1));
        AtomicLong nanoTime = new AtomicLong();
        PostcodeGatewayCircuitBreaker breaker = new PostcodeGatewayCircuitBreaker(properties, nanoTime::get);
        PostcodeGatewayHealthIndicator indicator = new PostcodeGatewayHealthIndicator(breaker);

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(0, indicator.health().getDetails().size());
        breaker.recordFailure();
        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
        assertEquals(0, indicator.health().getDetails().size());
    }
}

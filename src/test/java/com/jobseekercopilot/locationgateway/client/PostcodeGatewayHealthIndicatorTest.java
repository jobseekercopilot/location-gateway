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
        properties.setCircuitFailureThreshold(1);
        properties.setCircuitOpenDuration(Duration.ofSeconds(1));
        AtomicLong nanoTime = new AtomicLong();
        PostcodeGatewayCircuitBreaker breaker = new PostcodeGatewayCircuitBreaker(properties, nanoTime::get);
        PostcodeGatewayHealthIndicator indicator = new PostcodeGatewayHealthIndicator(breaker);

        assertEquals(Status.UP, indicator.health().getStatus());
        breaker.recordFailure();
        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
        assertEquals("OPEN", indicator.health().getDetails().get("circuit"));
    }
}

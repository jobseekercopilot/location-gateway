package com.jobseekercopilot.locationgateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobseekercopilot.locationgateway.config.LocationControlProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CallerRateLimiterTest {

    @Test
    void rejectsAtLimitWithRetryAfterAndResetsAfterWindow() {
        LocationControlProperties properties = properties();
        AtomicLong nanoTime = new AtomicLong();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CallerRateLimiter limiter = new CallerRateLimiter(properties, metrics, nanoTime::get);

        assertDoesNotThrow(() -> limiter.check("192.0.2.1"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.1"));
        LocationRateLimitException rejected = assertThrows(
                LocationRateLimitException.class, () -> limiter.check("192.0.2.1"));

        assertEquals(10, rejected.getRetryAfterSeconds());
        nanoTime.addAndGet(Duration.ofSeconds(10).toNanos());
        assertDoesNotThrow(() -> limiter.check("192.0.2.1"));
        assertEquals(3, metrics.get("location.postcode.rate.requests").tag("result", "allowed").counter().count());
        assertEquals(1, metrics.get("location.postcode.rate.requests").tag("result", "rejected").counter().count());
    }

    @Test
    void boundsTrackedCallerStateWithoutUsingCallerMetricTags() {
        LocationControlProperties properties = properties();
        properties.setRateMaximumTrackedCallers(2);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CallerRateLimiter limiter = new CallerRateLimiter(properties, metrics, System::nanoTime);

        limiter.check("192.0.2.1");
        limiter.check("192.0.2.2");
        limiter.check("192.0.2.3");

        assertEquals(2, limiter.trackedCallerCount());
        assertEquals(1, metrics.get("location.postcode.rate.caller.evictions").counter().count());
        assertEquals(0, metrics.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .filter(tag -> tag.getValue().startsWith("192.0.2."))
                .count());
    }

    private static LocationControlProperties properties() {
        LocationControlProperties properties = new LocationControlProperties();
        properties.setRateMaximumRequests(2);
        properties.setRateWindow(Duration.ofSeconds(10));
        return properties;
    }
}

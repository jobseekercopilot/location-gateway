package com.jobseekercopilot.locationgateway.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobseekercopilot.locationgateway.config.LocationControlProperties;
import com.jobseekercopilot.locationgateway.model.Location;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LocationLookupCacheTest {

    @Test
    void returnsDefensiveCopyWithinTtlAndExpiresWithoutPostcodeMetricTags() {
        LocationControlProperties properties = properties();
        AtomicLong nanoTime = new AtomicLong();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        LocationLookupCache cache = new LocationLookupCache(properties, metrics, nanoTime::get);
        Location source = location("LS1");

        assertTrue(cache.get("LS1").isEmpty());
        cache.put("LS1", source);
        Location cached = cache.get("LS1").orElseThrow();

        assertNotSame(source, cached);
        assertEquals("LS1", cached.getPostcode());
        cached.setPostcode("CHANGED");
        assertEquals("LS1", cache.get("LS1").orElseThrow().getPostcode());
        nanoTime.addAndGet(Duration.ofMinutes(1).toNanos());
        assertTrue(cache.get("LS1").isEmpty());
        assertEquals(0, cache.size());
        assertEquals(2, metrics.get("location.postcode.cache.requests").tag("result", "hit").counter().count());
        assertEquals(2, metrics.get("location.postcode.cache.requests").tag("result", "miss").counter().count());
        assertFalse(metrics.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getValue().contains("LS1")));
    }

    @Test
    void evictsLeastRecentlyUsedEntryAtTheConfiguredBound() {
        LocationControlProperties properties = properties();
        properties.setCacheMaximumEntries(2);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        LocationLookupCache cache = new LocationLookupCache(properties, metrics, System::nanoTime);

        cache.put("LS1", location("LS1"));
        cache.put("M1", location("M1"));
        assertTrue(cache.get("LS1").isPresent());
        cache.put("B1", location("B1"));

        assertEquals(2, cache.size());
        assertTrue(cache.get("LS1").isPresent());
        assertTrue(cache.get("B1").isPresent());
        assertTrue(cache.get("M1").isEmpty());
        assertEquals(1, metrics.get("location.postcode.cache.evictions").counter().count());
    }

    private static LocationControlProperties properties() {
        LocationControlProperties properties = new LocationControlProperties();
        properties.setCacheTtl(Duration.ofMinutes(1));
        return properties;
    }

    private static Location location(String postcode) {
        return new Location(postcode, "Name", postcode, "Region", 1.0, 2.0);
    }
}

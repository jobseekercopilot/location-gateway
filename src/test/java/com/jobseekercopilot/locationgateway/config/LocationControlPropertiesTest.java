package com.jobseekercopilot.locationgateway.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocationControlPropertiesTest {

    @Test
    void acceptsSafeDefaults() {
        assertDoesNotThrow(new LocationControlProperties()::validate);
    }

    @Test
    void rejectsUnboundedCacheAndRateConfiguration() {
        assertInvalid(properties -> properties.setCacheTtl(Duration.ofDays(2)));
        assertInvalid(properties -> properties.setCacheMaximumEntries(0));
        assertInvalid(properties -> properties.setRateMaximumRequests(0));
        assertInvalid(properties -> properties.setRateWindow(Duration.ZERO));
        assertInvalid(properties -> properties.setRateMaximumTrackedCallers(100_001));
    }

    private void assertInvalid(java.util.function.Consumer<LocationControlProperties> change) {
        LocationControlProperties properties = new LocationControlProperties();
        change.accept(properties);
        assertThrows(IllegalStateException.class, properties::validate);
    }
}

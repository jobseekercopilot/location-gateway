package com.jobseekercopilot.locationgateway.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorrelationIdFilterTest {

    @Test
    void redactsPostcodePathSegment() {
        assertEquals("/api/postcodes/{postcode}",
                CorrelationIdFilter.safePath("/api/postcodes/SW1A1AA"));
    }

    @Test
    void leavesNonPostcodePathsUnchanged() {
        assertEquals("/actuator/health", CorrelationIdFilter.safePath("/actuator/health"));
    }
}

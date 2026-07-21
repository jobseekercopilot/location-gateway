package com.jobseekercopilot.locationgateway.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobseekercopilot.locationgateway.exception.InvalidPlaceSearchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceSearchValidatorTest {

    @Test
    void normalisesBoundedUnicodePlaceNames() {
        assertEquals("St. Albans", PlaceSearchValidator.canonicalise("  St.   Albans "));
        assertEquals("King's Lynn", PlaceSearchValidator.canonicalise("King's Lynn"));
        assertEquals("Ynys Môn", PlaceSearchValidator.canonicalise("Ynys Môn"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "Leeds?limit=100", "../London", "London/Westminster", "Line\nBreak"})
    void rejectsMissingShortOrUnsafeQueries(String query) {
        assertThrows(InvalidPlaceSearchException.class,
                () -> PlaceSearchValidator.canonicalise(query));
    }

    @Test
    void rejectsExcessiveQueries() {
        assertThrows(InvalidPlaceSearchException.class,
                () -> PlaceSearchValidator.canonicalise("L".repeat(81)));
    }
}

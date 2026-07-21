package com.jobseekercopilot.locationgateway.validation;

import com.jobseekercopilot.locationgateway.exception.InvalidPlaceSearchException;
import java.util.regex.Pattern;

public final class PlaceSearchValidator {
    public static final int RESULT_LIMIT = 10;
    private static final int MINIMUM_QUERY_LENGTH = 2;
    private static final int MAXIMUM_QUERY_LENGTH = 80;
    private static final Pattern SAFE_QUERY = Pattern.compile("[\\p{L}\\p{M} .'-]+");

    private PlaceSearchValidator() {
    }

    public static String canonicalise(String query) {
        if (query != null && query.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidPlaceSearchException();
        }
        String clean = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (clean.length() < MINIMUM_QUERY_LENGTH
                || clean.length() > MAXIMUM_QUERY_LENGTH
                || !SAFE_QUERY.matcher(clean).matches()) {
            throw new InvalidPlaceSearchException();
        }
        return clean;
    }
}

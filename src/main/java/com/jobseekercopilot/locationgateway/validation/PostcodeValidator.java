package com.jobseekercopilot.locationgateway.validation;

import com.jobseekercopilot.locationgateway.exception.InvalidPostcodeException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PostcodeValidator {

    private static final Pattern UK_POSTCODE_OR_OUTCODE = Pattern.compile(
            "^(?:GIR(?:0AA)?|(?:[A-Z][1-9][0-9]?|[A-Z][A-HJ-Y][1-9][0-9]?|"
                    + "[A-Z][1-9][A-HJ-Z]|[A-Z][A-HJ-Y][1-9][A-HJ-Z])"
                    + "(?:[0-9][ABD-HJLNP-UW-Z]{2})?)$"
    );

    private PostcodeValidator() {
    }

    public static String canonicalise(String value) {
        if (value == null) {
            throw new InvalidPostcodeException();
        }

        String canonical = value.replaceAll("\\s+", "").toUpperCase(Locale.UK);
        if (canonical.length() < 2 || canonical.length() > 7
                || !UK_POSTCODE_OR_OUTCODE.matcher(canonical).matches()) {
            throw new InvalidPostcodeException();
        }
        return canonical;
    }
}

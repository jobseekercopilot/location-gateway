package com.jobseekercopilot.locationgateway.exception;

import org.springframework.http.HttpStatus;

public class LocationLookupException extends RuntimeException {

    private final HttpStatus status;

    public LocationLookupException(HttpStatus status, Throwable cause) {
        super("Location lookup failed", cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

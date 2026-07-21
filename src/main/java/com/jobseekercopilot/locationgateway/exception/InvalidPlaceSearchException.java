package com.jobseekercopilot.locationgateway.exception;

public class InvalidPlaceSearchException extends RuntimeException {
    public InvalidPlaceSearchException() {
        super("Invalid place search query.");
    }
}

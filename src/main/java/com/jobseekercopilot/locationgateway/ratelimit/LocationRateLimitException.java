package com.jobseekercopilot.locationgateway.ratelimit;

public class LocationRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public LocationRateLimitException(long retryAfterSeconds) {
        super("Location request rate limit exceeded.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

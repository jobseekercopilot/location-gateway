package com.jobseekercopilot.locationgateway.client;

public class PostcodeGatewayCircuitOpenException extends RuntimeException {
    public PostcodeGatewayCircuitOpenException() {
        super("Postcode gateway circuit is open.");
    }
}

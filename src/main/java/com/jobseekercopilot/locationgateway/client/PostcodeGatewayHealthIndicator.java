package com.jobseekercopilot.locationgateway.client;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("postcodeGateway")
public class PostcodeGatewayHealthIndicator implements HealthIndicator {
    private final PostcodeGatewayCircuitBreaker circuitBreaker;

    public PostcodeGatewayHealthIndicator(PostcodeGatewayCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Health health() {
        return circuitBreaker.isOpen()
                ? Health.outOfService().build()
                : Health.up().build();
    }
}

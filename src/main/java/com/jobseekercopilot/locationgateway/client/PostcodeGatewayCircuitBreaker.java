package com.jobseekercopilot.locationgateway.client;

import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostcodeGatewayCircuitBreaker {
    enum State { CLOSED, HALF_OPEN, OPEN }

    private final int failureThreshold;
    private final long openDurationNanos;
    private final LongSupplier nanoTime;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAt;
    private boolean halfOpenProbeInFlight;

    @Autowired
    public PostcodeGatewayCircuitBreaker(PostcodeGatewayProperties properties) {
        this(properties, System::nanoTime);
    }

    PostcodeGatewayCircuitBreaker(PostcodeGatewayProperties properties, LongSupplier nanoTime) {
        properties.validate();
        this.failureThreshold = properties.getCircuitFailureThreshold();
        this.openDurationNanos = properties.getCircuitOpenDuration().toNanos();
        this.nanoTime = nanoTime;
    }

    public synchronized boolean tryAcquirePermission() {
        advanceAfterOpenWindow();
        if (state == State.OPEN) {
            return false;
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenProbeInFlight) {
                return false;
            }
            halfOpenProbeInFlight = true;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenProbeInFlight = false;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN || ++consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = nanoTime.getAsLong();
            halfOpenProbeInFlight = false;
        }
    }

    public synchronized String stateName() {
        advanceAfterOpenWindow();
        return state.name();
    }

    public synchronized boolean isOpen() {
        advanceAfterOpenWindow();
        return state == State.OPEN;
    }

    private void advanceAfterOpenWindow() {
        if (state == State.OPEN && nanoTime.getAsLong() - openedAt >= openDurationNanos) {
            state = State.HALF_OPEN;
            halfOpenProbeInFlight = false;
        }
    }
}

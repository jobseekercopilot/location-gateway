package com.jobseekercopilot.locationgateway.ratelimit;

import com.jobseekercopilot.locationgateway.config.LocationControlProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CallerRateLimiter {
    private final int maximumRequests;
    private final long windowNanos;
    private final int maximumTrackedCallers;
    private final LongSupplier nanoTime;
    private final Map<String, Window> callers = new LinkedHashMap<>(16, 0.75f, true);
    private final Counter allowed;
    private final Counter rejected;
    private final Counter callerEvictions;

    @Autowired
    public CallerRateLimiter(LocationControlProperties properties, MeterRegistry meterRegistry) {
        this(properties, meterRegistry, System::nanoTime);
    }

    CallerRateLimiter(
            LocationControlProperties properties,
            MeterRegistry meterRegistry,
            LongSupplier nanoTime) {
        properties.validate();
        this.maximumRequests = properties.getRateMaximumRequests();
        this.windowNanos = properties.getRateWindow().toNanos();
        this.maximumTrackedCallers = properties.getRateMaximumTrackedCallers();
        this.nanoTime = nanoTime;
        this.allowed = Counter.builder("location.postcode.rate.requests")
                .tag("result", "allowed").register(meterRegistry);
        this.rejected = Counter.builder("location.postcode.rate.requests")
                .tag("result", "rejected").register(meterRegistry);
        this.callerEvictions = Counter.builder("location.postcode.rate.caller.evictions")
                .register(meterRegistry);
    }

    public synchronized void check(String remoteAddress) {
        String caller = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        long now = nanoTime.getAsLong();
        Window current = callers.get(caller);
        if (current == null || now - current.startedAtNanos >= windowNanos) {
            retainCapacityFor(caller);
            callers.put(caller, new Window(now, 1));
            allowed.increment();
            return;
        }
        if (current.requestCount >= maximumRequests) {
            rejected.increment();
            long remainingNanos = Math.max(1, windowNanos - (now - current.startedAtNanos));
            long retryAfterSeconds = Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
            throw new LocationRateLimitException(retryAfterSeconds);
        }
        callers.put(caller, new Window(current.startedAtNanos, current.requestCount + 1));
        allowed.increment();
    }

    synchronized int trackedCallerCount() {
        return callers.size();
    }

    private void retainCapacityFor(String caller) {
        if (callers.containsKey(caller) || callers.size() < maximumTrackedCallers) {
            return;
        }
        Iterator<String> oldest = callers.keySet().iterator();
        if (oldest.hasNext()) {
            oldest.next();
            oldest.remove();
            callerEvictions.increment();
        }
    }

    private record Window(long startedAtNanos, int requestCount) {
    }
}

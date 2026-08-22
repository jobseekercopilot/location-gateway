package com.jobseekercopilot.locationgateway.client;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.generated.postcodeiogateway.model.PlaceLocation;
import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ResilientPostcodeGatewayClient {
    private final PostcodeApi postcodeApi;
    private final PostcodeGatewayCircuitBreaker circuitBreaker;
    private final PostcodeGatewayProperties properties;
    private final Sleeper sleeper;
    private final MeterRegistry meterRegistry;
    private final Counter retries;
    private final Counter circuitRejections;

    @Autowired
    public ResilientPostcodeGatewayClient(
            PostcodeApi postcodeApi,
            PostcodeGatewayCircuitBreaker circuitBreaker,
            PostcodeGatewayProperties properties,
            MeterRegistry meterRegistry) {
        this(postcodeApi, circuitBreaker, properties, meterRegistry,
                duration -> Thread.sleep(duration.toMillis()));
    }

    ResilientPostcodeGatewayClient(
            PostcodeApi postcodeApi,
            PostcodeGatewayCircuitBreaker circuitBreaker,
            PostcodeGatewayProperties properties,
            MeterRegistry meterRegistry,
            Sleeper sleeper) {
        properties.validate();
        this.postcodeApi = postcodeApi;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.sleeper = sleeper;
        this.retries = Counter.builder("location.postcode.provider.retries").register(meterRegistry);
        this.circuitRejections = Counter.builder("location.postcode.provider.circuit.rejections")
                .register(meterRegistry);
    }

    public PostcodeLocation lookup(String postcode) {
        return execute(() -> postcodeApi.getLocationByPostcode(postcode));
    }

    public List<PlaceLocation> searchPlaces(String query, int limit) {
        return execute(() -> {
            List<PlaceLocation> result = postcodeApi.searchPlaces(query, limit);
            if (result == null || result.size() > limit || result.stream().anyMatch(place -> !validPlace(place))) {
                throw new RestClientException("Postcode gateway returned an invalid place response.");
            }
            return List.copyOf(result);
        });
    }

    private boolean validPlace(PlaceLocation place) {
        return place != null
                && hasText(place.getId(), 128)
                && hasText(place.getName(), 200)
                && hasText(place.getPostcode(), 8)
                && hasText(place.getRegion(), 100)
                && boundedCoordinate(place.getLatitude(), -90, 90)
                && boundedCoordinate(place.getLongitude(), -180, 180);
    }

    private boolean hasText(String value, int maximumLength) {
        return value != null && !value.isBlank() && value.length() <= maximumLength;
    }

    private boolean boundedCoordinate(Double value, double minimum, double maximum) {
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private <T> T execute(ProviderCall<T> providerCall) {
        if (!circuitBreaker.tryAcquirePermission()) {
            circuitRejections.increment();
            throw new PostcodeGatewayCircuitOpenException();
        }

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            long startedAt = System.nanoTime();
            try {
                T result = providerCall.execute();
                recordProviderRequest(startedAt, "success");
                circuitBreaker.recordSuccess();
                return result;
            } catch (RestClientException exception) {
                recordProviderRequest(startedAt, outcome(exception));
                boolean retryable = isRetryable(exception);
                if (!retryable) {
                    if (isCircuitFailure(exception)) {
                        circuitBreaker.recordFailure();
                    } else {
                        circuitBreaker.recordSuccess();
                    }
                    throw exception;
                }
                if (attempt == properties.getMaxAttempts()) {
                    circuitBreaker.recordFailure();
                    throw exception;
                }
                retries.increment();
                sleep(backoff(attempt));
            }
        }
        throw new IllegalStateException("Postcode gateway retry loop ended unexpectedly.");
    }

    private void recordProviderRequest(long startedAt, String outcome) {
        meterRegistry.timer("location.postcode.provider.requests", "outcome", outcome)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private String outcome(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            return "transport_error";
        }
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    ? "server_error"
                    : "client_error";
        }
        return "invalid_response";
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.is5xxServerError()
                    || status.value() == 408
                    || status.value() == 425
                    || status.value() == 429;
        }
        return false;
    }

    private boolean isCircuitFailure(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    || responseException.getStatusCode().value() == 408
                    || responseException.getStatusCode().value() == 425
                    || responseException.getStatusCode().value() == 429;
        }
        return true;
    }

    private Duration backoff(int completedAttempt) {
        long multiplier = 1L << Math.min(completedAttempt - 1, 10);
        Duration candidate;
        try {
            candidate = properties.getInitialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            candidate = properties.getMaxBackoff();
        }
        return candidate.compareTo(properties.getMaxBackoff()) > 0
                ? properties.getMaxBackoff()
                : candidate;
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            circuitBreaker.recordFailure();
            throw new ResourceAccessException("Postcode gateway retry interrupted.");
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T execute();
    }
}

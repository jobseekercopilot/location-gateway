package com.jobseekercopilot.locationgateway.client;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.locationgateway.config.PostcodeGatewayProperties;
import java.time.Duration;
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

    @Autowired
    public ResilientPostcodeGatewayClient(
            PostcodeApi postcodeApi,
            PostcodeGatewayCircuitBreaker circuitBreaker,
            PostcodeGatewayProperties properties) {
        this(postcodeApi, circuitBreaker, properties, duration -> Thread.sleep(duration.toMillis()));
    }

    ResilientPostcodeGatewayClient(
            PostcodeApi postcodeApi,
            PostcodeGatewayCircuitBreaker circuitBreaker,
            PostcodeGatewayProperties properties,
            Sleeper sleeper) {
        properties.validate();
        this.postcodeApi = postcodeApi;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public PostcodeLocation lookup(String postcode) {
        if (!circuitBreaker.tryAcquirePermission()) {
            throw new PostcodeGatewayCircuitOpenException();
        }

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                PostcodeLocation result = postcodeApi.getLocationByPostcode(postcode);
                circuitBreaker.recordSuccess();
                return result;
            } catch (RestClientException exception) {
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
                sleep(backoff(attempt));
            }
        }
        throw new IllegalStateException("Postcode gateway retry loop ended unexpectedly.");
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
}

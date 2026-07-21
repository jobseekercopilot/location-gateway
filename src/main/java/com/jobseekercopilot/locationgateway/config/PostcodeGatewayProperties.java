package com.jobseekercopilot.locationgateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "postcode.io.gateway")
public class PostcodeGatewayProperties {
    private String url = "http://localhost:8082";
    private Duration connectTimeout = Duration.ofMillis(500);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int maxAttempts = 2;
    private Duration initialBackoff = Duration.ofMillis(100);
    private Duration maxBackoff = Duration.ofMillis(250);
    private int circuitFailureThreshold = 5;
    private Duration circuitOpenDuration = Duration.ofSeconds(30);

    public void validate() {
        requireBounded(connectTimeout, Duration.ofMillis(1), Duration.ofSeconds(30), "connect timeout");
        requireBounded(readTimeout, Duration.ofMillis(1), Duration.ofSeconds(30), "read timeout");
        requireBounded(initialBackoff, Duration.ofMillis(1), Duration.ofSeconds(5), "initial backoff");
        requireBounded(maxBackoff, Duration.ofMillis(1), Duration.ofSeconds(5), "max backoff");
        requireBounded(circuitOpenDuration, Duration.ofMillis(1), Duration.ofMinutes(10),
                "circuit open duration");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("POSTCODE_IO_GATEWAY_URL must not be blank.");
        }
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalStateException("Postcode gateway max attempts must be between 1 and 3.");
        }
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalStateException("Postcode gateway initial backoff must not exceed max backoff.");
        }
        if (circuitFailureThreshold < 1 || circuitFailureThreshold > 20) {
            throw new IllegalStateException("Postcode gateway circuit failure threshold must be between 1 and 20.");
        }
    }

    private static void requireBounded(Duration value, Duration minimum, Duration maximum, String name) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException("Postcode gateway " + name + " is outside the safe range.");
        }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int circuitFailureThreshold) { this.circuitFailureThreshold = circuitFailureThreshold; }
    public Duration getCircuitOpenDuration() { return circuitOpenDuration; }
    public void setCircuitOpenDuration(Duration circuitOpenDuration) { this.circuitOpenDuration = circuitOpenDuration; }
}

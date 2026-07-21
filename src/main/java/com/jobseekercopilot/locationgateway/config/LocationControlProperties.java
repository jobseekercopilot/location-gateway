package com.jobseekercopilot.locationgateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "location.lookup")
public class LocationControlProperties {
    private Duration cacheTtl = Duration.ofMinutes(15);
    private int cacheMaximumEntries = 10_000;
    private int rateMaximumRequests = 120;
    private Duration rateWindow = Duration.ofMinutes(1);
    private int rateMaximumTrackedCallers = 20_000;

    public void validate() {
        requireDuration(cacheTtl, Duration.ofSeconds(1), Duration.ofHours(24), "cache TTL");
        requireRange(cacheMaximumEntries, 1, 100_000, "cache maximum entries");
        requireRange(rateMaximumRequests, 1, 10_000, "rate maximum requests");
        requireDuration(rateWindow, Duration.ofSeconds(1), Duration.ofHours(1), "rate window");
        requireRange(rateMaximumTrackedCallers, 1, 100_000, "rate maximum tracked callers");
    }

    private static void requireDuration(Duration value, Duration minimum, Duration maximum, String name) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException("Location lookup " + name + " is outside the safe range.");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalStateException("Location lookup " + name + " is outside the safe range.");
        }
    }

    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    public int getCacheMaximumEntries() { return cacheMaximumEntries; }
    public void setCacheMaximumEntries(int cacheMaximumEntries) { this.cacheMaximumEntries = cacheMaximumEntries; }
    public int getRateMaximumRequests() { return rateMaximumRequests; }
    public void setRateMaximumRequests(int rateMaximumRequests) { this.rateMaximumRequests = rateMaximumRequests; }
    public Duration getRateWindow() { return rateWindow; }
    public void setRateWindow(Duration rateWindow) { this.rateWindow = rateWindow; }
    public int getRateMaximumTrackedCallers() { return rateMaximumTrackedCallers; }
    public void setRateMaximumTrackedCallers(int rateMaximumTrackedCallers) {
        this.rateMaximumTrackedCallers = rateMaximumTrackedCallers;
    }
}

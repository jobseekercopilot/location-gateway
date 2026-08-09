package com.jobseekercopilot.locationgateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "location.service")
public class LocationServiceProperties {
    private String url = "http://localhost:8104";
    private Duration connectTimeout = Duration.ofMillis(500);
    private Duration readTimeout = Duration.ofSeconds(5);
    private String serviceToken;

    public void validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("LOCATION_SERVICE_URL must not be blank.");
        }
        if (serviceToken == null || serviceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("LOCATION_SERVICE_TOKEN must contain at least 32 bytes.");
        }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }
}

package com.jobseekercopilot.locationgateway.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.client.ApiClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;

class PostcodeIoGatewayConfigTest {

    @Test
    void configuredReadDeadlineBoundsASlowRealHttpResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/postcodes/LS1", exchange -> {
            try {
                Thread.sleep(200);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            PostcodeGatewayProperties properties = new PostcodeGatewayProperties();
            properties.setServiceToken("test-only-location-service-token-32-bytes");
            properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setConnectTimeout(Duration.ofMillis(50));
            properties.setReadTimeout(Duration.ofMillis(25));
            ApiClient apiClient = new PostcodeIoGatewayConfig(properties, new RestTemplateBuilder())
                    .postcodeIoApiClient();

            assertThrows(ResourceAccessException.class,
                    () -> new PostcodeApi(apiClient).getLocationByPostcode("LS1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsafeRetryConfigurationAtStartup() {
        PostcodeGatewayProperties properties = new PostcodeGatewayProperties();
        properties.setServiceToken("test-only-location-service-token-32-bytes");
        properties.setMaxAttempts(4);

        assertThrows(IllegalStateException.class,
                () -> new PostcodeIoGatewayConfig(properties, new RestTemplateBuilder())
                        .postcodeIoApiClient());
    }
}

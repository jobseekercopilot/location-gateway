package com.jobseekercopilot.locationgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.locationgateway.ProviderHttpStub.StubResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LocationGatewayRateLimitApplicationIntegrationTest {
    private static final ProviderHttpStub PROVIDER = ProviderHttpStub.start();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("postcode.io.gateway.url", PROVIDER::baseUrl);
        registry.add("postcode.io.gateway.max-attempts", () -> "1");
        registry.add("location.lookup.rate-maximum-requests", () -> "2");
        registry.add("location.lookup.rate-window", () -> "2s");
        registry.add("location.lookup.rate-maximum-tracked-callers", () -> "10");
        registry.add("debug", () -> "false");
    }

    @BeforeEach
    void resetProvider() {
        PROVIDER.reset();
        PROVIDER.respond(request -> {
            String postcode = request.rawPath().substring(request.rawPath().lastIndexOf('/') + 1);
            return StubResponse.json(200, """
                    {"postcode":"%s","region":"Test region","admin_district":"Test district",
                     "latitude":51.5,"longitude":-0.1}
                    """.formatted(postcode));
        });
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Test
    void callerThrottleRejectsBeforeProviderWithBoundedRetryAfter() {
        assertThat(get("/api/postcodes/LS1", "198.51.100.1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/postcodes/M1", "198.51.100.2").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> rejected = get("/api/postcodes/B1", "198.51.100.3");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getBody()).containsEntry("message", "Too many location requests. Try again later.");
        assertThat(rejected.getBody().toString()).doesNotContain("B1").doesNotContain("198.51.100.3");
        assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .satisfies(value -> {
                    assertThat(value).matches("[1-9][0-9]*");
                    assertThat(Long.parseLong(value)).isBetween(1L, 2L);
                });
        assertThat(PROVIDER.requestCount()).isEqualTo(2);
    }

    private ResponseEntity<Map> get(String path, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Forwarded-For", forwardedFor);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}

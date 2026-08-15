package com.jobseekercopilot.locationgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.locationgateway.ProviderHttpStub.RecordedRequest;
import com.jobseekercopilot.locationgateway.ProviderHttpStub.StubResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LocationGatewayApplicationIntegrationTest {
    private static final ProviderHttpStub PROVIDER = ProviderHttpStub.start();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("postcode.io.gateway.url", PROVIDER::baseUrl);
        registry.add("postcode.io.gateway.connect-timeout", () -> "100ms");
        registry.add("postcode.io.gateway.read-timeout", () -> "100ms");
        registry.add("postcode.io.gateway.max-attempts", () -> "2");
        registry.add("postcode.io.gateway.initial-backoff", () -> "1ms");
        registry.add("postcode.io.gateway.max-backoff", () -> "1ms");
        registry.add("postcode.io.gateway.circuit-failure-threshold", () -> "2");
        registry.add("postcode.io.gateway.circuit-open-duration", () -> "10s");
        registry.add("location.lookup.rate-maximum-requests", () -> "100");
        registry.add("location.lookup.rate-window", () -> "1m");
        registry.add("location.lookup.cache-ttl", () -> "1m");
        registry.add("location.lookup.cache-maximum-entries", () -> "100");
        registry.add("debug", () -> "false");
    }

    @BeforeEach
    void resetProvider() {
        PROVIDER.reset();
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Test
    void fullPostcodeAndOutcodeUseCanonicalPathsAndPropagateCorrelation() {
        PROVIDER.respond(request -> {
            if (request.rawPath().endsWith("SW1A1AA")) {
                return StubResponse.json(200, postcode("SW1A 1AA", "London", "Westminster", 51.501, -0.141));
            }
            return StubResponse.json(200, postcode("LS1", "Yorkshire and the Humber", "Leeds", 53.8008, -1.5491));
        });

        HttpHeaders headers = jsonHeaders();
        headers.set("X-Correlation-Id", "loc06-safe-correlation");
        ResponseEntity<Map> full = restTemplate.exchange(
                uri("/api/postcodes/SW1A%201AA"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        ResponseEntity<Map> outcode = get("/api/postcodes/ls1");

        assertThat(full.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(full.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("loc06-safe-correlation");
        assertThat(location(full, 0))
                .containsEntry("postcode", "SW1A 1AA")
                .containsEntry("name", "Westminster, London")
                .containsEntry("region", "London")
                .containsEntry("latitude", 51.501)
                .containsEntry("longitude", -0.141);
        assertThat(outcode.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(location(outcode, 0)).containsEntry("postcode", "LS1");
        assertThat(PROVIDER.requests()).extracting(RecordedRequest::rawPath)
                .containsExactly("/api/postcodes/SW1A1AA", "/api/postcodes/LS1");
        assertThat(PROVIDER.requests()).extracting(RecordedRequest::method)
                .containsExactly("GET", "GET");
        assertThat(PROVIDER.requests().get(0).firstHeader("X-Correlation-Id"))
                .isEqualTo("loc06-safe-correlation");
    }

    @Test
    void placeSearchMapsMultipleAndEmptyResultsThroughExactBoundedQuery() {
        PROVIDER.respond(request -> request.rawQuery().contains("Nowhere")
                ? StubResponse.json(200, "[]")
                : StubResponse.json(200, """
                        [{"id":"place-1","name":"St Albans, Hertfordshire","postcode":"AL1",
                          "region":"East of England","adminDistrict":"St Albans",
                          "latitude":51.7527,"longitude":-0.3394,"futureField":"ignored"},
                         {"id":"place-2","name":"St Albans, Devon","postcode":"TQ10",
                          "region":"South West","adminDistrict":"South Hams",
                          "latitude":50.42,"longitude":-3.82}]
                        """));

        ResponseEntity<Map> matches = get("/api/locations?q=St%20Albans");
        ResponseEntity<Map> empty = get("/api/locations?q=Nowhere");

        assertThat(matches.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(matches.getBody()).containsEntry("message", "Locations retrieved.");
        assertThat(locations(matches)).hasSize(2);
        assertThat(location(matches, 0))
                .containsEntry("id", "place-1")
                .containsEntry("postcode", "AL1")
                .containsEntry("region", "East of England");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).containsEntry("message", "No matching locations.");
        assertThat(locations(empty)).isEmpty();
        assertThat(PROVIDER.requests()).extracting(RecordedRequest::rawPath)
                .containsExactly("/api/places", "/api/places");
        assertThat(PROVIDER.requests()).extracting(RecordedRequest::rawQuery)
                .containsExactly("q=St%20Albans&limit=10", "q=Nowhere&limit=10");
        assertThat(PROVIDER.requests()).extracting(RecordedRequest::method)
                .containsExactly("GET", "GET");
    }

    @Test
    void invalidInputsFailBeforeProviderWithoutEchoingValues(CapturedOutput output) {
        ResponseEntity<Map> postcode = get("/api/postcodes/INVALID");
        ResponseEntity<Map> place = get("/api/locations?q=SECRET!");

        assertThat(postcode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postcode.getBody()).containsEntry("message", "Invalid postcode or outcode.");
        assertThat(place.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(place.getBody()).containsEntry("message", "Invalid place search query.");
        assertThat(postcode.getBody().toString()).doesNotContain("INVALID");
        assertThat(place.getBody().toString()).doesNotContain("SECRET");
        assertThat(PROVIDER.requestCount()).isZero();
        assertThat(output).doesNotContain("INVALID").doesNotContain("SECRET!");
    }

    @Test
    void terminalNotFoundAndExhaustedRateLimitPreserveStableStatuses(CapturedOutput output) {
        PROVIDER.enqueue(
                StubResponse.json(404, "{\"message\":\"private missing body\"}"),
                StubResponse.json(429, "{\"message\":\"private throttle body\"}"),
                StubResponse.json(429, "{\"message\":\"private throttle body\"}"));

        ResponseEntity<Map> missing = get("/api/postcodes/LS1");
        ResponseEntity<Map> throttled = get("/api/postcodes/M1");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("message", "Location not found.");
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getBody()).containsEntry("message", "Too many location requests. Try again later.");
        assertThat(missing.getBody().toString()).doesNotContain("private");
        assertThat(throttled.getBody().toString()).doesNotContain("private");
        assertThat(PROVIDER.requestCount()).isEqualTo(3);
        assertThat(output)
                .doesNotContain("private missing body")
                .doesNotContain("private throttle body")
                .doesNotContain("LS1")
                .doesNotContain("M1")
                .doesNotContain(PROVIDER.baseUrl());
    }

    @Test
    void unsupportedCoverageIsNotRetriedAndProviderDetailIsRedacted(CapturedOutput output) {
        PROVIDER.enqueue(StubResponse.json(
                422,
                "{\"code\":\"POSTCODE_COVERAGE_UNSUPPORTED\",\"message\":\"private BT detail\"}"));

        ResponseEntity<Map> response = get("/api/postcodes/BT11AA");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody())
                .containsEntry("statusCode", 422)
                .containsEntry("success", false)
                .containsEntry("message", "This postcode area is not currently supported.");
        assertThat(response.getBody().toString()).doesNotContain("BT11AA", "private");
        assertThat(PROVIDER.requestCount()).isEqualTo(1);
        assertThat(output).doesNotContain("BT11AA").doesNotContain("private BT detail");
    }

    @Test
    void transientRateLimitRetriesOnceAndReturnsRecoveredResult() {
        PROVIDER.enqueue(
                StubResponse.json(429, "{\"message\":\"retry\"}"),
                StubResponse.json(200, postcode("LS1", "Yorkshire and the Humber", "Leeds", 53.8, -1.55)));

        ResponseEntity<Map> response = get("/api/postcodes/LS1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(location(response, 0)).containsEntry("postcode", "LS1");
        assertThat(PROVIDER.requestCount()).isEqualTo(2);
    }

    @Test
    void slowProviderReturnsBoundedGatewayTimeoutWithoutRetry(CapturedOutput output) {
        PROVIDER.respond(request -> StubResponse.delayedJson(
                200,
                postcode("SW1A 1AA", "London", "Westminster", 51.501, -0.141),
                Duration.ofMillis(300)));

        ResponseEntity<Map> response = get("/api/postcodes/SW1A1AA");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).containsEntry("message", "Location service timed out.");
        assertThat(PROVIDER.requestCount()).isEqualTo(1);
        assertThat(output).doesNotContain("SW1A1AA").doesNotContain(PROVIDER.baseUrl());
    }

    @Test
    void connectionFailuresOpenCircuitAndRejectTheNextCallLocally(CapturedOutput output) {
        PROVIDER.respond(request -> StubResponse.connectionDrop());

        ResponseEntity<Map> first = get("/api/postcodes/LS1");
        ResponseEntity<Map> second = get("/api/postcodes/M1");
        ResponseEntity<Map> circuit = get("/api/postcodes/B1");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(circuit.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(PROVIDER.requestCount()).isEqualTo(2);
        assertThat(output).doesNotContain("LS1").doesNotContain("M1").doesNotContain("B1");
    }

    @Test
    void malformedProviderJsonReturnsRedactedBadGateway(CapturedOutput output) {
        PROVIDER.respond(request -> StubResponse.json(200, "{private-malformed-json"));

        ResponseEntity<Map> response = get("/api/postcodes/LS1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("message", "Location service returned an invalid response.");
        assertThat(response.getBody().toString()).doesNotContain("private-malformed-json");
        assertThat(output).doesNotContain("private-malformed-json").doesNotContain("LS1");
        assertThat(PROVIDER.requestCount()).isEqualTo(1);
    }

    @Test
    void successfulCanonicalLookupsAreCachedAndFailuresAreNot() {
        PROVIDER.respond(request -> request.rawPath().endsWith("M1")
                ? StubResponse.json(404, "{\"message\":\"not found\"}")
                : StubResponse.json(200, postcode("LS1", "Yorkshire and the Humber", "Leeds", 53.8, -1.55)));

        assertThat(get("/api/postcodes/ls1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/postcodes/LS1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/postcodes/M1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/postcodes/M1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(PROVIDER.requests()).extracting(RecordedRequest::rawPath)
                .containsExactly("/api/postcodes/LS1", "/api/postcodes/M1", "/api/postcodes/M1");
    }

    private ResponseEntity<Map> get(String path) {
        return restTemplate.exchange(uri(path), HttpMethod.GET, new HttpEntity<>(jsonHeaders()), Map.class);
    }

    private URI uri(String path) {
        return URI.create(restTemplate.getRootUri() + path);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> locations(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("locations");
    }

    private static Map<String, Object> location(ResponseEntity<Map> response, int index) {
        return locations(response).get(index);
    }

    private static String postcode(
            String value,
            String region,
            String adminDistrict,
            double latitude,
            double longitude) {
        return """
                {"postcode":"%s","country":"England","region":"%s",
                 "admin_district":"%s","latitude":%s,"longitude":%s}
                """.formatted(value, region, adminDistrict, latitude, longitude);
    }
}

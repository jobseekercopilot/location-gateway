package com.jobseekercopilot.locationgateway.client;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.client.ApiClient;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PostcodeGatewayContractTest {

    @Test
    void generatedClientCallsBoundedPlaceSearchAndDeserializesAdditiveResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PostcodeApi postcodeApi = new PostcodeApi(
                new ApiClient(restTemplate).setBasePath("http://postcode.test"));
        server.expect(once(), requestTo("http://postcode.test/api/places?q=St%20Albans&limit=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "id":"place-1",
                          "name":"St Albans",
                          "postcode":"AL1",
                          "region":"East of England",
                          "adminDistrict":"St Albans",
                          "latitude":51.7527,
                          "longitude":-0.3394,
                          "futureField":"ignored"
                        }]
                        """, MediaType.APPLICATION_JSON));

        var places = postcodeApi.searchPlaces("St Albans", 2);

        assertEquals(1, places.size());
        assertEquals("place-1", places.get(0).getId());
        assertEquals("AL1", places.get(0).getPostcode());
        server.verify();
    }

    @Test
    void generatedClientCallsVersionedPathAndDeserializesProviderResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiClient apiClient = new ApiClient(restTemplate).setBasePath("http://postcode.test");
        PostcodeApi postcodeApi = new PostcodeApi(apiClient);

        server.expect(once(), requestTo("http://postcode.test/api/postcodes/SW1A1AA"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "postcode": "SW1A 1AA",
                          "country": "England",
                          "region": "London",
                          "admin_district": "Westminster",
                          "latitude": 51.501009,
                          "longitude": -0.141588
                        }
                        """, MediaType.APPLICATION_JSON));

        PostcodeLocation location = postcodeApi.getLocationByPostcode("SW1A1AA");

        assertEquals("SW1A 1AA", location.getPostcode());
        assertEquals("London", location.getRegion());
        assertEquals("Westminster", location.getAdminDistrict());
        assertEquals(51.501009, location.getLatitude());
        assertEquals(-0.141588, location.getLongitude());
        server.verify();
    }

    @Test
    void generatedClientRejectsMissingRequiredPostcodeBeforeCallingProvider() {
        ApiClient apiClient = new ApiClient(new RestTemplate()).setBasePath("http://postcode.test");
        PostcodeApi postcodeApi = new PostcodeApi(apiClient);

        HttpClientErrorException exception = assertThrows(
                HttpClientErrorException.class,
                () -> postcodeApi.getLocationByPostcode(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {
            "BAD_REQUEST", "NOT_FOUND", "TOO_MANY_REQUESTS", "BAD_GATEWAY",
            "SERVICE_UNAVAILABLE", "GATEWAY_TIMEOUT"
    })
    void generatedClientPreservesDocumentedProviderErrorStatus(HttpStatus status) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PostcodeApi postcodeApi = new PostcodeApi(
                new ApiClient(restTemplate).setBasePath("http://postcode.test")
        );
        server.expect(once(), requestTo("http://postcode.test/api/postcodes/LS1"))
                .andRespond(withStatus(status));

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> postcodeApi.getLocationByPostcode("LS1")
        );

        assertEquals(status, exception.getStatusCode());
        server.verify();
    }
}

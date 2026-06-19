package com.jobseekercopilot.locationgateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

@Component
public class PostcodeIoClient {

    private WebClient webClient;

    @Value("${postcode.io.gateway.url}")
    private String postcodeIoGatewayUrl;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(postcodeIoGatewayUrl)
                .build();
    }

    public Mono<PostcodeIoLocation> getPostcodeDetails(String postcode) {
        return webClient.get()
                .uri("/api/postcodes/" + postcode)
                .retrieve()
                .bodyToMono(PostcodeIoLocation.class);
    }

    @Getter
    @Setter
    public static class PostcodeIoLocation {
        private String postcode;
        private String region;
        @JsonProperty("admin_district")
        private String adminDistrict;
    }
}
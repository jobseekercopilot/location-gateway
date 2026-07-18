package com.jobseekercopilot.locationgateway.config;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostcodeIoGatewayConfig {

    @Value("${postcode.io.gateway.url}")
    private String postcodeIoGatewayUrl;

    @Bean
    public ApiClient postcodeIoApiClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(postcodeIoGatewayUrl);
        return apiClient;
    }

    @Bean
    public PostcodeApi postcodeIoGatewayApi(ApiClient postcodeIoApiClient) {
        return new PostcodeApi(postcodeIoApiClient);
    }
}
package com.jobseekercopilot.locationgateway.config;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.client.ApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PostcodeGatewayProperties.class)
public class PostcodeIoGatewayConfig {
    private final PostcodeGatewayProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    public PostcodeIoGatewayConfig(
            PostcodeGatewayProperties properties,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Bean
    public ApiClient postcodeIoApiClient() {
        properties.validate();
        return new ApiClient(restTemplateBuilder
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .defaultHeader("X-Service-Token", properties.getServiceToken())
                .build())
                .setBasePath(properties.getUrl());
    }

    @Bean
    public PostcodeApi postcodeIoGatewayApi(ApiClient postcodeIoApiClient) {
        return new PostcodeApi(postcodeIoApiClient);
    }
}

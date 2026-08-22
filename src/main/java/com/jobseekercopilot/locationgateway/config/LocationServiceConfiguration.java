package com.jobseekercopilot.locationgateway.config;

import com.jobseekercopilot.generated.locationservice.api.LocationApi;
import com.jobseekercopilot.generated.locationservice.client.ApiClient;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocationServiceProperties.class)
public class LocationServiceConfiguration {
    @Bean
    ApiClient locationServiceApiClient(
            LocationServiceProperties properties,
            RestTemplateBuilder restTemplateBuilder) {
        properties.validate();
        ApiClient client = new ApiClient(restTemplateBuilder
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build())
                .setBasePath(properties.getUrl());
        client.setApiKey(properties.getServiceToken());
        return client;
    }

    @Bean
    LocationApi locationServiceApi(ApiClient locationServiceApiClient) {
        return new LocationApi(locationServiceApiClient);
    }
}

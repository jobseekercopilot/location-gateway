package com.jobseekercopilot.locationgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jobseeker Copilot - Location Gateway API")
                        .description("Gateway API for UK location searches and postcode lookups. Coordinates with postcode-io-gateway for location data.")
                        .version("1.0.0"));
    }
}
package com.jobseekercopilot.locationgateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocationControlProperties.class)
public class LocationControlsConfiguration {
}

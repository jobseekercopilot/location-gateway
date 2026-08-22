package com.jobseekercopilot.locationgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private String id;
    private String name;
    private String postcode;
    private String region;
    private Double latitude;
    private Double longitude;
}

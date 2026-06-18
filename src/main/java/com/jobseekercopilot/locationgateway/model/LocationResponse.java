package com.jobseekercopilot.locationgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {
    private int statusCode;
    private boolean success;
    private String message;
    private List<Location> locations;
}
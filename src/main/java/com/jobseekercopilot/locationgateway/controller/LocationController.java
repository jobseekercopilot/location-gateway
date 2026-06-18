package com.jobseekercopilot.locationgateway.controller;

import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.model.LocationResponse;
import com.jobseekercopilot.locationgateway.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LocationController {
    
    @Autowired
    private LocationService locationService;
    
    @GetMapping("/locations")
    public ResponseEntity<LocationResponse> searchLocations(@RequestParam(name = "q") String query) {
        if (query == null || query.trim().isEmpty()) {
            LocationResponse response = new LocationResponse(
                400,
                false,
                "Missing search query parameter. 'q' must be provided.",
                null
            );
            return ResponseEntity.badRequest().body(response);
        }
        
        List<Location> locations = locationService.searchLocations(query);
        String message = "Retrieved " + locations.size() + " matching UK location" + 
                         (locations.size() != 1 ? "s." : ".");
        
        LocationResponse response = new LocationResponse(
            200,
            true,
            message,
            locations
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/postcodes/{postcode}")
    public Mono<ResponseEntity<LocationResponse>> getLocationByPostcode(@PathVariable String postcode) {
        return locationService.getLocationFromPostcodeIo(postcode)
                .map(location -> {
                    LocationResponse response = new LocationResponse(
                        200,
                        true,
                        "Retrieved location for postcode " + postcode + ".",
                        List.of(location)
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    LocationResponse response = new LocationResponse(
                        500,
                        false,
                        "Error retrieving location for postcode " + postcode + ": " + e.getMessage(),
                        null
                    );
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
                });
    }
}

package com.jobseekercopilot.locationgateway.controller;

import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.model.LocationResponse;
import com.jobseekercopilot.locationgateway.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Location", description = "UK location search and postcode lookup endpoints")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/postcodes/{postcode}")
    @Operation(
            summary = "Get location by postcode",
            description = "Retrieves location details for a UK postcode via postcode-io-gateway."
    )
    public ResponseEntity<LocationResponse> getLocationByPostcode(
            @Parameter(description = "UK postcode", required = true, example = "SW1A 1AA")
            @PathVariable String postcode) {

        try {
            Location location = locationService.getLocationFromPostcodeIo(postcode);

            return ResponseEntity.ok(new LocationResponse(
                    200,
                    true,
                    "Retrieved location for postcode " + postcode + ".",
                    List.of(location)
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new LocationResponse(
                    500,
                    false,
                    "Error retrieving location for postcode " + postcode + ": " + e.getMessage(),
                    null
            ));
        }
    }
}
package com.jobseekercopilot.locationgateway.controller;

import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.model.LocationResponse;
import com.jobseekercopilot.locationgateway.ratelimit.CallerRateLimiter;
import com.jobseekercopilot.locationgateway.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Location", description = "UK location search and postcode lookup endpoints")
public class LocationController {

    private final LocationService locationService;
    private final CallerRateLimiter callerRateLimiter;

    @GetMapping(value = "/locations", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search UK places", description = "Returns at most ten matching UK places.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed"),
            @ApiResponse(responseCode = "400", description = "Invalid place query",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "429", description = "Request or provider rate limited",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "502", description = "Invalid provider response",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "503", description = "Location provider unavailable",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "504", description = "Location provider timed out",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    public ResponseEntity<LocationResponse> searchLocations(
            @Parameter(description = "Place-name query", required = true, example = "Leeds")
            @RequestParam(value = "q", defaultValue = "") String query,
            HttpServletRequest request) {
        callerRateLimiter.check(request.getRemoteAddr());
        List<Location> locations = locationService.searchLocations(query);
        return ResponseEntity.ok(new LocationResponse(
                200,
                true,
                locations.isEmpty() ? "No matching locations." : "Locations retrieved.",
                locations));
    }

    @GetMapping(value = "/postcodes/{postcode}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get location by postcode",
            description = "Retrieves location details for a UK postcode via postcode-io-gateway."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location found"),
            @ApiResponse(responseCode = "400", description = "Invalid postcode or outcode",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "422", description = "Postcode area is outside approved coverage",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "429", description = "Request rate limited",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "502", description = "Invalid provider response",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "503", description = "Location provider unavailable",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "504", description = "Location provider timed out",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    public ResponseEntity<LocationResponse> getLocationByPostcode(
            @Parameter(description = "UK postcode", required = true, example = "SW1A 1AA")
            @PathVariable String postcode,
            HttpServletRequest request) {

        callerRateLimiter.check(request.getRemoteAddr());
        Location location = locationService.getLocationFromPostcodeIo(postcode);
        return ResponseEntity.ok(new LocationResponse(
                200,
                true,
                "Location retrieved.",
                List.of(location)
        ));
    }
}

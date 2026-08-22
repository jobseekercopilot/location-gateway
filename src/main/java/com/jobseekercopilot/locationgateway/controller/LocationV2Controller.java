package com.jobseekercopilot.locationgateway.controller;

import com.jobseekercopilot.locationgateway.client.LocationDomainClient;
import com.jobseekercopilot.locationgateway.model.LocationV2Contracts;
import com.jobseekercopilot.locationgateway.model.LocationResponse;
import com.jobseekercopilot.locationgateway.ratelimit.CallerRateLimiter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/locations")
public class LocationV2Controller {
    private final LocationDomainClient locations;
    private final CallerRateLimiter rateLimiter;

    public LocationV2Controller(LocationDomainClient locations, CallerRateLimiter rateLimiter) {
        this.locations = locations;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/autocomplete")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bounded location suggestions"),
            @ApiResponse(responseCode = "422", description = "Postcode area is outside approved coverage",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    public LocationV2Contracts.AutocompleteResponse autocomplete(
            @Valid @RequestBody LocationV2Contracts.AutocompleteRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest.getRemoteAddr());
        return locations.autocomplete(request);
    }

    @PostMapping("/resolve")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location resolution completed"),
            @ApiResponse(responseCode = "422", description = "Postcode area is outside approved coverage",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    public LocationV2Contracts.ResolveResponse resolve(
            @Valid @RequestBody LocationV2Contracts.ResolveRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest.getRemoteAddr());
        return locations.resolve(request);
    }
}

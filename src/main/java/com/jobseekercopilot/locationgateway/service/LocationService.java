package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.locationgateway.model.Location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final PostcodeApi postcodeIoGatewayApi;

    public Location getLocationFromPostcodeIo(String postcode) {
        long startedAt = System.nanoTime();
        log.info("Location lookup started postcodePresent={}", postcode != null && !postcode.isBlank());
        var result = postcodeIoGatewayApi.getLocationByPostcode(postcode);

        String region = result.getRegion();
        String adminDistrict = result.getAdminDistrict();

        String formattedName =
            (adminDistrict != null ? adminDistrict : "")
            + (region != null && !region.isBlank() ? ", " + region : "");

        Location location = new Location(
            postcode,
            formattedName,
            postcode,
            region,
            result.getLatitude(),
            result.getLongitude()
        );
        log.info("Location lookup completed postcodePresent={} regionPresent={} adminDistrictPresent={} durationMs={}",
                postcode != null && !postcode.isBlank(),
                region != null && !region.isBlank(),
                adminDistrict != null && !adminDistrict.isBlank(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return location;
    }
}

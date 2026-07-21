package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.locationgateway.client.PostcodeGatewayCircuitOpenException;
import com.jobseekercopilot.locationgateway.client.ResilientPostcodeGatewayClient;
import com.jobseekercopilot.locationgateway.cache.LocationLookupCache;
import com.jobseekercopilot.locationgateway.exception.LocationLookupException;
import com.jobseekercopilot.locationgateway.model.Location;
import com.jobseekercopilot.locationgateway.validation.PostcodeValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final ResilientPostcodeGatewayClient postcodeGatewayClient;
    private final LocationLookupCache locationLookupCache;

    public Location getLocationFromPostcodeIo(String postcode) {
        String canonicalPostcode = PostcodeValidator.canonicalise(postcode);
        long startedAt = System.nanoTime();
        log.info("Location lookup started");
        var cached = locationLookupCache.get(canonicalPostcode);
        if (cached.isPresent()) {
            log.info("Location lookup completed cacheHit=true durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return cached.get();
        }
        var result = lookup(canonicalPostcode);

        String region = result.getRegion();
        String adminDistrict = result.getAdminDistrict();

        String formattedName =
            (adminDistrict != null ? adminDistrict : "")
            + (region != null && !region.isBlank() ? ", " + region : "");

        String responsePostcode = result.getPostcode() == null || result.getPostcode().isBlank()
                ? canonicalPostcode
                : result.getPostcode();
        Location location = new Location(
            responsePostcode,
            formattedName,
            responsePostcode,
            region,
            result.getLatitude(),
            result.getLongitude()
        );
        locationLookupCache.put(canonicalPostcode, location);
        log.info("Location lookup completed cacheHit=false regionPresent={} adminDistrictPresent={} durationMs={}",
                region != null && !region.isBlank(),
                adminDistrict != null && !adminDistrict.isBlank(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return location;
    }

    private com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation lookup(String postcode) {
        try {
            return postcodeGatewayClient.lookup(postcode);
        } catch (PostcodeGatewayCircuitOpenException exception) {
            throw new LocationLookupException(HttpStatus.SERVICE_UNAVAILABLE, exception);
        } catch (RestClientResponseException exception) {
            throw new LocationLookupException(mapStatus(exception.getStatusCode().value()), exception);
        } catch (ResourceAccessException exception) {
            throw new LocationLookupException(
                    hasTimeoutCause(exception) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (RestClientException exception) {
            throw new LocationLookupException(HttpStatus.BAD_GATEWAY, exception);
        }
    }

    private HttpStatus mapStatus(int upstreamStatus) {
        return switch (upstreamStatus) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 408, 504 -> HttpStatus.GATEWAY_TIMEOUT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 502 -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private boolean hasTimeoutCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 10; depth++) {
            String type = current.getClass().getSimpleName();
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || type.contains("ReadTimeout")
                    || type.contains("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.jobseekercopilot.locationgateway.controller;

import com.jobseekercopilot.locationgateway.exception.InvalidPostcodeException;
import com.jobseekercopilot.locationgateway.exception.InvalidPlaceSearchException;
import com.jobseekercopilot.locationgateway.exception.LocationLookupException;
import com.jobseekercopilot.locationgateway.model.LocationResponse;
import com.jobseekercopilot.locationgateway.ratelimit.LocationRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidPostcodeException.class)
    ResponseEntity<LocationResponse> handleInvalidPostcode() {
        return error(HttpStatus.BAD_REQUEST, "Invalid postcode or outcode.");
    }

    @ExceptionHandler(InvalidPlaceSearchException.class)
    ResponseEntity<LocationResponse> handleInvalidPlaceSearch() {
        return error(HttpStatus.BAD_REQUEST, "Invalid place search query.");
    }

    @ExceptionHandler(LocationRateLimitException.class)
    ResponseEntity<LocationResponse> handleCallerRateLimit(LocationRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(response(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many location requests. Try again later."));
    }

    @ExceptionHandler(LocationLookupException.class)
    ResponseEntity<LocationResponse> handleLookupFailure(LocationLookupException exception) {
        HttpStatus status = exception.getStatus();
        log.warn("Location provider request failed status={} causeType={}",
                status.value(), exception.getCause().getClass().getSimpleName());
        return error(status, switch (status) {
            case BAD_REQUEST -> "Invalid postcode or outcode.";
            case NOT_FOUND -> "Location not found.";
            case TOO_MANY_REQUESTS -> "Too many location requests. Try again later.";
            case SERVICE_UNAVAILABLE -> "Location service is temporarily unavailable.";
            case GATEWAY_TIMEOUT -> "Location service timed out.";
            default -> "Location service returned an invalid response.";
        });
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<LocationResponse> handleMalformedJson() {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<LocationResponse> handleUnsupportedContentType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<LocationResponse> handleUnexpectedFailure(Exception exception) {
        log.error("Unexpected location request failure causeType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected location service error.");
    }

    private ResponseEntity<LocationResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(response(status, message));
    }

    private LocationResponse response(HttpStatus status, String message) {
        return new LocationResponse(
                status.value(), false, message, List.of()
        );
    }
}

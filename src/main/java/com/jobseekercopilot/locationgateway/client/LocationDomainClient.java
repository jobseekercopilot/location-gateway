package com.jobseekercopilot.locationgateway.client;

import com.jobseekercopilot.generated.locationservice.api.LocationApi;
import com.jobseekercopilot.generated.locationservice.model.Attribution;
import com.jobseekercopilot.generated.locationservice.model.FieldProvenance;
import com.jobseekercopilot.generated.locationservice.model.ProviderReference;
import com.jobseekercopilot.locationgateway.model.LocationV2Contracts;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocationDomainClient {
    private final LocationApi api;

    public LocationDomainClient(LocationApi api) {
        this.api = api;
    }

    public LocationV2Contracts.AutocompleteResponse autocomplete(LocationV2Contracts.AutocompleteRequest request) {
        var generatedRequest = new com.jobseekercopilot.generated.locationservice.model.AutocompleteRequest()
                .input(request.input())
                .sessionId(uuid(request.sessionId()))
                .countryCodes(request.countryCodes());
        var response = api.autocompleteLocation(generatedRequest);
        return new LocationV2Contracts.AutocompleteResponse(
                text(response.getSessionId()),
                safe(response.getSuggestions()).stream()
                        .map(value -> new LocationV2Contracts.Suggestion(
                                value.getSuggestionId(),
                                value.getPrimaryText(),
                                value.getSecondaryText(),
                                text(value.getPrecisionHint())))
                        .toList(),
                attribution(response.getAttribution()),
                text(response.getSource()));
    }

    public LocationV2Contracts.ResolveResponse resolve(LocationV2Contracts.ResolveRequest request) {
        var response = api.resolveLocation(
                new com.jobseekercopilot.generated.locationservice.model.ResolveRequest()
                        .sessionId(uuid(request.sessionId()))
                        .suggestionId(request.suggestionId()));
        return new LocationV2Contracts.ResolveResponse(
                canonical(response.getLocation()),
                text(response.getResolutionStatus()),
                attribution(response.getAttribution()),
                response.getReasonCode());
    }

    private LocationV2Contracts.CanonicalLocation canonical(
            com.jobseekercopilot.generated.locationservice.model.CanonicalLocation value) {
        return new LocationV2Contracts.CanonicalLocation(
                value.getLocationId(),
                value.getDisplayName(),
                value.getCountryCode(),
                value.getPostcode(),
                value.getLocality(),
                value.getRegion(),
                decimal(value.getLatitude()),
                decimal(value.getLongitude()),
                text(value.getLocationType()),
                text(value.getPrecision()),
                text(value.getConfidence()),
                safe(value.getProviderReferences()).stream().map(this::reference).toList(),
                safe(value.getFieldProvenance()).stream().map(this::provenance).toList(),
                instant(value.getNormalisedAt()));
    }

    private LocationV2Contracts.ProviderReference reference(ProviderReference value) {
        return new LocationV2Contracts.ProviderReference(
                text(value.getProvider()), value.getExternalId(), instant(value.getObservedAt()));
    }

    private LocationV2Contracts.FieldProvenance provenance(FieldProvenance value) {
        return new LocationV2Contracts.FieldProvenance(
                value.getField(), text(value.getSource()), text(value.getMethod()),
                instant(value.getObservedAt()), text(value.getConfidence()));
    }

    private LocationV2Contracts.Attribution attribution(Attribution value) {
        return new LocationV2Contracts.Attribution(
                Boolean.TRUE.equals(value.getRequired()), text(value.getProvider()));
    }

    private UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}

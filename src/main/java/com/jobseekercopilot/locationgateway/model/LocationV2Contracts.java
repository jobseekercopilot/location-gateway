package com.jobseekercopilot.locationgateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LocationV2Contracts {
    private LocationV2Contracts() { }

    public record AutocompleteRequest(
            @NotBlank @Size(min = 3, max = 200) String input,
            String sessionId,
            List<String> countryCodes) { }
    public record AutocompleteResponse(
            String sessionId, List<Suggestion> suggestions, Attribution attribution, String source) { }
    public record Suggestion(String suggestionId, String primaryText, String secondaryText, String precisionHint) { }
    public record Attribution(boolean required, String provider) { }
    public record ResolveRequest(@NotBlank String sessionId, @NotBlank String suggestionId) { }
    public record ResolveResponse(
            CanonicalLocation location, String resolutionStatus, Attribution attribution, String reasonCode) { }
    public record CanonicalLocation(
            UUID locationId, String displayName, String countryCode, String postcode, String locality, String region,
            BigDecimal latitude, BigDecimal longitude, String locationType, String precision, String confidence,
            List<ProviderReference> providerReferences, List<FieldProvenance> fieldProvenance, Instant normalisedAt) { }
    public record ProviderReference(String provider, String externalId, Instant observedAt) { }
    public record FieldProvenance(
            String field, String source, String method, Instant observedAt, String confidence) { }
}

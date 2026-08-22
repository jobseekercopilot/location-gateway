# Location gateway testing runbook

## Test boundaries

`LocationGatewayApplicationIntegrationTest` starts the complete Spring Boot
application on a random port and calls its public API over HTTP. A
repository-owned JDK `HttpServer` binds only to `127.0.0.1` on an operating
system-assigned port. Production controller, validation, caller throttling,
service, cache, retry, circuit and generated-client wiring remain in the path.

The stub is deterministic, is cleared between scenarios and has no route to
Postcodes.io. Test responses must use invented or public reference locations;
never put credentials, user data or copied provider datasets in fixtures. The
stub records only the request data needed for assertions and is stopped after
the suite.

## Scenario matrix

| Boundary | Evidence |
|---|---|
| Full postcode and outcode | Canonical exact provider paths, stable response mapping and correlation propagation |
| Place search | Exact encoded `q` and fixed `limit=10`, multiple and empty results, additive provider field compatibility |
| Validation | Invalid postcode and place query return redacted `400` before provider access |
| Provider statuses | Terminal `404`, one bounded `429` retry, exhausted `429`, exact call counts and redacted bodies |
| Provider transport | Slow response maps to `504` without retry; connection failures open the circuit and the next call is rejected locally with `503` |
| Provider content | Malformed JSON maps to a redacted `502` |
| Cache | Canonical successful lookups are reused; failed lookups are not cached |
| Caller throttle | Forwarded-header spoofing does not split direct-caller capacity; rejection occurs before provider access with bounded positive `Retry-After` |
| Privacy | Negative-path bodies and captured logs omit submitted values, provider bodies and provider URL |

Focused unit, MVC, generated-client contract, configuration, readiness,
resilience, cache/rate and telemetry tests remain authoritative for their
smaller boundaries. The application tests complement rather than replace them.

## Commands

Run the application boundary only:

```bash
mvn -B -Dtest=LocationGatewayApplicationIntegrationTest,LocationGatewayRateLimitApplicationIntegrationTest test
```

Run all repository verification and dependency-policy tests:

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
```

Run the release-shaped container build, startup, readiness, outage and image
scan checks:

```bash
./scripts/verify-container.sh
```

`DEBUG=false` or `APP_LOG_LEVEL=INFO` can be set locally to suppress verbose
framework diagnostics; this does not change test assertions.

## Cross-service and browser ownership

POSTCODE-06 owns deterministic testing of the real postcode gateway provider
boundary and consumes the paths asserted here. Cross-service smoke evidence
must start the approved gateway revisions without contacting an uncontrolled
external provider.

CLIENT-07 owns browser evidence. It must reuse and extend the existing
Playwright/Cucumber framework, including location success and safe failure
journeys, rather than introduce another browser framework. This repository does
not claim browser or complete system beta-readiness from its application suite.

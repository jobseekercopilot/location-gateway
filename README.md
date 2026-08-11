# Location Gateway

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| Browser-facing UK location facade with validation and rate limits | Client Express BFF | Location Service (including compatibility postcode routes) | None | 8081 |

V2 autocomplete and resolution call `location-service`; the gateway never
receives the Google credential. The original bounded GET place/postcode routes
remain as compatibility contracts routed to Location Service. See the central
[location journey](https://docs.jobseekercopilot.com/journeys/location/) and
[service catalogue](https://docs.jobseekercopilot.com/services/catalogue/).

Spring Boot facade that maps UK postcode/outcode and bounded place-name queries
through the provider-neutral Location Service into the client response.

> Delivery status: implemented and composed for controlled private-beta use.
> Downstream calls are bounded and validation/error semantics are stable;
> remaining production operational controls are retained in
> [the audit](docs/BETA_READINESS_AUDIT.md).

Location acquisition is an upstream profile concern, not part of provider
fan-out. That boundary is defined in the Infrastructure
[Job Search architecture ADR](https://github.com/jobseekercopilot/infrastructure/blob/develop/docs/adr/0001-job-search-architecture-and-ownership.md).

## Requirements and configuration

- Java 17 and Maven 3.9
- location-service

| Variable | Local default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `LOCATION_SERVICE_URL` | `http://localhost:8104` | Provider-neutral location service |
| `LOCATION_SERVICE_TOKEN` | none, required | Internal caller identity; at least 32 bytes |
| `LOCATION_SERVICE_CONNECT_TIMEOUT` / `LOCATION_SERVICE_READ_TIMEOUT` | `500ms` / `5s` | V2/compatibility downstream deadlines |
| `POSTCODE_GATEWAY_CONNECT_TIMEOUT` / `POSTCODE_GATEWAY_READ_TIMEOUT` | `500ms` / `5s` | Connection and per-attempt response deadlines |
| `POSTCODE_GATEWAY_MAX_ATTEMPTS` | `2` | Maximum attempts for retryable idempotent responses |
| `POSTCODE_GATEWAY_INITIAL_BACKOFF` / `POSTCODE_GATEWAY_MAX_BACKOFF` | `100ms` / `250ms` | Bounded retry backoff |
| `POSTCODE_GATEWAY_CIRCUIT_FAILURE_THRESHOLD` / `POSTCODE_GATEWAY_CIRCUIT_OPEN_DURATION` | `5` / `30s` | Failed logical calls before open and recovery-probe delay |
| `LOCATION_CACHE_TTL` / `LOCATION_CACHE_MAXIMUM_ENTRIES` | `15m` / `10000` | Successful normalized postcode cache lifetime and per-instance bound |
| `LOCATION_RATE_MAXIMUM_REQUESTS` / `LOCATION_RATE_WINDOW` | `120` / `1m` | Requests allowed per direct caller and fixed window |
| `LOCATION_RATE_MAXIMUM_TRACKED_CALLERS` | `20000` | Bounded per-instance caller state |
| `APP_LOG_LEVEL` | `INFO` | Application log level |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info` | Actuator endpoints; metrics require an approved private operations network |

## API, health and build

- `GET /api/postcodes/{postcode}` accepts a valid UK postcode or outcode,
  canonicalises it before lookup, and returns stable `400`, `404`, `429`,
  `502`, `503` and `504` errors.
- `GET /api/locations?q={place-name}` accepts a restricted 2–80 character
  query and returns at most ten matches through the same safe failure policy.
- `POST /api/v2/locations/autocomplete` and `/resolve` expose opaque,
  provider-attributed v2 sessions through Location Service.
- `/v3/api-docs`, `/swagger-ui/index.html`, `/actuator/health`
- `/actuator/health/readiness` (aggregate application and downstream-circuit readiness)

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
./scripts/verify-container.sh
mvn spring-boot:run
```

The full verification includes a loopback-only application integration suite
that exercises the public HTTP API through the real generated postcode client.
It never calls Postcodes.io. See the [testing runbook](docs/TESTING.md) for the
scenario matrix, focused commands, fixture rules and cross-service ownership.

Release-shaped container verification runs the full suite before copying the
verified JAR into a digest-pinned image. The runtime is read-only, uses fixed
UID/GID `10001:10001`, supports graceful shutdown and receives a blocking
Critical/High image scan in CI. See [service operations](docs/OPERATIONS.md).

CI scans the resolved runtime dependency set with pinned Trivy releases,
publishes the JSON report, and rejects unaccepted Critical or High findings.
See [dependency security](docs/DEPENDENCY_SECURITY.md) for local reproduction,
scanner scope, and the time-bounded exception process.

Maven generates the postcode RestTemplate client from the versioned consumer
contract at `src/main/openapi/postcode-io-gateway.yaml`. OpenAPI Generator 7.24.0
is pinned, output stays under `target/generated-sources`, and neither a sibling
checkout nor `libs/*.jar` is required. See the
[contract update procedure](src/main/openapi/README.md) before changing the
provider API. Do not commit generated Java or client binaries.

See [bounded place-name search](docs/LOCATION_SEARCH.md) for the provider/public
contracts, validation, privacy, deterministic test approach and residual
coverage limitations.

Successful lookups use a bounded in-memory cache keyed only by the canonical
postcode/outcode. Direct callers have a bounded fixed-window capacity limit;
`429` includes `Retry-After`. Provider latency/outcome, retry, circuit, cache,
and rate metrics never contain postcode or caller labels. See
[location controls](docs/LOCATION_CONTROLS.md) for privacy, tuning, beta alert
thresholds, trusted-proxy constraints, and residual per-instance limitations.

The generated client uses explicit connect/read deadlines. Only transient HTTP
responses receive one bounded retry; transport timeouts are not retried. A
per-instance circuit rejects during an observed outage and contributes safe
readiness state. See the [downstream resilience policy](docs/DOWNSTREAM_RESILIENCE.md)
for budgets, error behavior, tuning, tests, ownership, and residual risk.

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` is not used for application delivery. Postcode values are
redacted from request-path logs, and error responses do not echo input or
downstream response details. Use the correlation ID to join gateway logs. For
client-generation failures, validate the checked-in contract and rerun
`mvn -B clean verify`.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).

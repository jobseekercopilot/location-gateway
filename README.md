# Location Gateway

Spring Boot facade that maps UK postcode/outcode and bounded place-name queries
through postcode-io-gateway into the client location response.

> Beta status: not beta-ready. Postcode and place-name behavior are bounded,
> but testing/operational blockers remain. Downstream calls are bounded and
> validation/error semantics are stable. See
> [the audit](docs/BETA_READINESS_AUDIT.md).

## Requirements and configuration

- Java 17 and Maven 3.9
- postcode-io-gateway

| Variable | Local default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `POSTCODE_IO_GATEWAY_URL` | `http://localhost:8082` | Postcode gateway |
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

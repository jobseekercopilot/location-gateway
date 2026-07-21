# Location Gateway

Spring Boot facade that currently maps a UK postcode/outcode through
postcode-io-gateway into the client location response.

> Beta status: not beta-ready. The advertised general search endpoint is
> absent and resilience, validation and operational blockers remain. See
> [the audit](docs/BETA_READINESS_AUDIT.md).

## Requirements and configuration

- Java 17 and Maven 3.9
- postcode-io-gateway

| Variable | Local default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `POSTCODE_IO_GATEWAY_URL` | `http://localhost:8082` | Postcode gateway |
| `APP_LOG_LEVEL` | `INFO` | Application log level |

## API, health and build

- `GET /api/postcodes/{postcode}` accepts a valid UK postcode or outcode,
  canonicalises it before lookup, and returns stable `400`, `404`, `429`,
  `502`, `503` and `504` errors.
- `/v3/api-docs`, `/swagger-ui/index.html`, `/actuator/health`

`GET /api/locations?q=` is not implemented; LOC-02 owns that decision.

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
mvn spring-boot:run
docker build -t location-gateway .
```

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

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` will be introduced later. Postcode values are
redacted from request-path logs, and error responses do not echo input or
downstream response details. Use the correlation ID to join gateway logs. For
client-generation failures, validate the checked-in contract and rerun
`mvn -B clean verify`.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).

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

- `GET /api/postcodes/{postcode}`
- `/v3/api-docs`, `/swagger-ui/index.html`, `/actuator/health`

`GET /api/locations?q=` is not implemented; LOC-02 owns that decision.

```bash
mvn -B clean verify
mvn spring-boot:run
docker build -t location-gateway .
```

Maven generates the postcode RestTemplate client from the versioned consumer
contract at `src/main/openapi/postcode-io-gateway.yaml`. OpenAPI Generator 7.5.0
is pinned, output stays under `target/generated-sources`, and neither a sibling
checkout nor `libs/*.jar` is required. See the
[contract update procedure](src/main/openapi/README.md) before changing the
provider API. Do not commit generated Java or client binaries.

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` will be introduced later. A 5xx postcode
response currently conflates invalid input and provider failure; follow the
correlation ID and the paired postcode gateway logs without recording postcode
PII unnecessarily. For client-generation failures, validate the checked-in
contract and rerun `mvn -B clean verify`.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).

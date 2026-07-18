# Location Gateway

Spring Boot facade that currently maps a UK postcode/outcode through
postcode-io-gateway into the client location response.

> Beta status: not beta-ready. The generated postcode client is not
> reproducible and the advertised general search endpoint is absent. See
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
mvn -B verify
mvn spring-boot:run
docker build -t location-gateway .
```

The clean build fails until LOC-01 replaces the local generated JAR. Do not
commit that binary.

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` will be introduced later. A 5xx postcode
response currently conflates invalid input and provider failure; follow the
correlation ID and the paired postcode gateway logs without recording postcode
PII unnecessarily.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).

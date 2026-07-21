# Service operations

## Verification

From a clean clone with Java 17, Maven 3.9 and Docker:

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
./scripts/verify-container.sh
```

The container validator builds only from the verified JAR, starts with an
unreachable disposable downstream, confirms non-root read-only operation,
opens the existing circuit through one bounded synthetic request, proves
readiness fails closed and proves graceful SIGTERM exit. CI scans the rebuilt
image for unaccepted Critical/High OS and library findings.

## Health, startup and shutdown

`/actuator/health` and `/actuator/health/readiness` return aggregate state only.
Readiness includes the passive postcode-gateway circuit and never creates a
downstream request. URLs, postcode/place values, response bodies and exception
details are not exposed. `/actuator/metrics` is private by default; platform
owners must use an approved authenticated collector.

The process allows 20 seconds for graceful lifecycle shutdown. Orchestrators
must allow at least 25 seconds before forcible termination.

## Troubleshooting and ownership

- Readiness non-UP: inspect bounded circuit/outcome/latency signals and
  downstream DNS/network/health. Wait for the half-open probe; do not restart
  every replica or log request values.
- `429`: inspect caller capacity and `Retry-After`; do not key controls from
  untrusted forwarded headers.
- `502`/`503`/`504`: use stable status and correlation metadata; never copy
  downstream bodies, URLs or submitted locations into logs.
- Missing telemetry: inspect the platform-owned private exporter, dashboard
  and alert routing. Do not expose metrics on public ingress.

Service owners maintain application controls, signal contracts and runbooks.
Platform owners maintain private collection, resource limits, network policy,
scan operations and alert delivery. LOC-06 and the blocked client completion of
LOC-02 remain separate end-to-end evidence risks.

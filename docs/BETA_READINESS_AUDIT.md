# Beta-readiness audit: location gateway

Audit date: 18 July 2026

Status: **Not beta-ready.** LOC-01 now generates the postcode client from a
versioned consumer contract, LOC-03 validates postcode input and preserves safe
error semantics, LOC-04 bounds the downstream call, and clean builds no longer
require an untracked JAR. The UI-advertised general search route does not exist,
and remaining testing and operational findings still block beta.

## Findings

| ID | Finding | Evidence | Risk and severity | Recommended solution and acceptance criteria | Dependencies | Beta blocker | Effort |
|---|---|---|---|---|---|---|---|
| [LOC-01](https://github.com/jobseekercopilot/location-gateway/issues/1) | Replace the postcode client `systemPath` JAR | `pom.xml` points at `libs/postcode-io-gateway-client-1.0.0.jar`; `libs/` is excluded. | **Critical / P0 build:** a fresh clone cannot compile. | Publish/generate a versioned client from an owned contract; remove `systemPath`; verify clean-clone build and contract compatibility. | Postcode contract/publishing. | Yes | L |
| [LOC-02](https://github.com/jobseekercopilot/location-gateway/issues/2) | Implement or remove location-name search | Client and README use `GET /api/locations?q=`, but controller only maps `/api/postcodes/{postcode}`. | **High / P1 functional:** location suggestions fail for names/partial input. | Agree provider/API ownership; implement bounded search or remove the feature/contract; test partial, empty and no-result cases. | Client decision/provider capability. | Yes | M |
| [LOC-03](https://github.com/jobseekercopilot/location-gateway/issues/3) | Validate postcode input and map provider errors | Controller accepts unconstrained path text, catches every exception as 500 and includes exception text and postcode in response. | **High / P1 API/security:** invalid/not-found/provider errors are indistinguishable and internal details leak. | Canonicalise and validate UK postcode/outcode; map 400/404/429/502/503/504 to stable errors; avoid echoing unnecessary PII; test malformed JSON/content types. | Postcode error contract. | Yes | M |
| [LOC-04](https://github.com/jobseekercopilot/location-gateway/issues/4) | Bound the synchronous provider call | **Remediated:** the generated client has validated connect/read deadlines, bounded transient-response retry/backoff, a per-instance circuit and circuit-derived readiness. | The cascading thread-exhaustion risk is bounded; each instance may still observe failures before its local circuit opens. | Retain slow/outage/recovery tests and documented budgets; tune only from measured latency/capacity. | LOC-01, postcode SLO and repository-owned resilience policy. | No | M |
| [LOC-05](https://github.com/jobseekercopilot/location-gateway/issues/5) | Add cache/rate controls and operational signals | No cache, caller rate limit, provider latency/error metric or alert requirement exists. | **Medium / P2 reliability/observability:** duplicate calls waste provider capacity and outages are invisible. | Define cache key/TTL/privacy, rate limit, metrics and beta alerts; test cache and throttling. | Monitoring approach. | No | M |
| [LOC-06](https://github.com/jobseekercopilot/location-gateway/issues/6) | Add meaningful contract/integration testing | Four tests cover a happy service mapping, controller shape and OpenAPI; no invalid/outage/rate/timeout/contract tests exist. | **High / P1 testing:** resilience/error semantics are unproven. | Add contract plus provider-stub integration tests for full postcode and all negative scenarios; include browser E2E. | LOC-01–05. | Yes | M |
| [LOC-07](https://github.com/jobseekercopilot/location-gateway/issues/7) | Harden container, readiness and docs | Docker skips tests and runs root/mutable images; health details are always exposed and no downstream readiness is defined. | **Medium / P1 operational/docs:** the image and runbook remain unsafe and incomplete for beta operation. | Pin/non-root/scan image, run verify, add dependency readiness/graceful shutdown, and document actual operations. | LOC-01/02. | Yes | M |
| [LOC-08](https://github.com/jobseekercopilot/location-gateway/issues/8) | Establish reliable dependency vulnerability scanning | CI emits `mvn dependency:tree` but performs no vulnerability analysis; no dependable advisory-feed cache or risk-acceptance workflow is configured. | **High / P1 dependency:** vulnerable gateway or HTTP libraries can reach beta without a reliable blocking signal. | Select a proprietary-compatible Maven scanner, configure authenticated/cached advisory data, publish a machine-readable report, fail on unaccepted Critical/High findings and document the risk-acceptance process. | Platform CI and advisory-feed decision. | Yes | M |

## LOC-01 remediation evidence

- `systemPath`, the ignored client JAR dependency and Docker `libs` copy are
  removed.
- The owned postcode consumer contract is versioned under `src/main/openapi`
  and records the reviewed provider repository revision.
- OpenAPI Generator 7.5.0 was pinned and emitted the RestTemplate client beneath
  `target/generated-sources`; generated sources and binaries remain ignored.
- Contract tests prove the generated request path, JSON field mapping and
  required postcode parameter behavior.
- Clean-clone Maven, Docker, dependency inventory, paired gateway journey and
  complete-history secret-scan evidence is recorded in LOC-01 and its pull
  request.

At completion of LOC-01, LOC-02 through LOC-08 remained open.

## LOC-08 remediation evidence

- The verified pre-remediation runtime set contained 83 Java packages and 47
  Critical/High findings (4 Critical and 43 High).
- Spring Boot was upgraded from 3.2.0 to 4.1.0, springdoc-openapi to 3.0.3,
  Lombok to 1.18.46, and OpenAPI Generator to 7.24.0. The Spring Boot 4 REST
  client and MVC test modules/package migrations were adopted.
- The post-remediation scan covered 111 packages with zero Critical or High
  findings. The generated RestTemplate client contract tests still pass.
- CI uses pinned Trivy and action revisions, caches advisory data, scans only
  Maven's resolved runtime dependency directory, uploads the JSON report, and
  applies a fail-closed policy after report generation.
- Policy tests reject Critical findings, malformed or uncovered reports, and
  missing, invalid, or expired risk-exception metadata. Full details are in
  `docs/DEPENDENCY_SECURITY.md`.

At completion of LOC-08, LOC-02 through LOC-07 remained open and the service was
still **not beta-ready**.

## LOC-03 remediation evidence

- UK postcodes and outcodes are whitespace-normalised, upper-cased and checked
  against bounded UK formats before a downstream request is made.
- Provider `400`, `404`, `429`, `502`, `503` and `504` responses are mapped to
  stable public errors; timeouts and unexpected provider responses have explicit
  gateway semantics.
- Responses do not echo submitted postcodes or downstream exception bodies, and
  request logging replaces the postcode path segment with `{postcode}`.
- MVC and service tests cover success, invalid input, every required provider
  status, connection/client failures, malformed-body/content-type handlers and
  log-path redaction. The checked-in consumer contract records the reviewed
  postcode gateway revision.

## LOC-04 remediation evidence

- The Spring-managed generated client applies a 500 ms connect deadline and
  five-second read deadline by default; startup rejects unsafe bounds.
- One bounded retry with backoff applies only to explicit transient responses.
  Caller errors and blocking transport timeouts are not retried.
- A concurrency-safe per-instance circuit opens after failed logical calls,
  rejects locally, permits one half-open probe and closes after recovery.
- The `postcodeGateway` health contributor reports circuit-derived safe state
  without making a synthetic downstream request.
- Focused tests cover a slow real HTTP response, retry and no-retry paths,
  outage/open rejection, half-open recovery, readiness and stable public mapping.
- `docs/DOWNSTREAM_RESILIENCE.md` records budgets, tuning rules, ownership and
  residual risk.

This resolves LOC-03 and LOC-04 only. LOC-02 and LOC-05 through LOC-07 remain open, so the
service is still **not beta-ready**.

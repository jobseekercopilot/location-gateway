# Beta-readiness audit: location gateway

Audit date: 18 July 2026

Status: **Not beta-ready.** LOC-01 now generates the postcode client from a
versioned consumer contract, LOC-03 validates postcode input and preserves safe
error semantics, LOC-04 bounds the downstream call, LOC-05 adds bounded
cache/rate/telemetry controls, LOC-07 hardens runtime operations, and clean builds no longer require an untracked
JAR. LOC-02 implements the UI-advertised bounded place-name route. Remaining
testing and operational findings still block beta.

## Findings

| ID | Finding | Evidence | Risk and severity | Recommended solution and acceptance criteria | Dependencies | Beta blocker | Effort |
|---|---|---|---|---|---|---|---|
| [LOC-01](https://github.com/jobseekercopilot/location-gateway/issues/1) | Replace the postcode client `systemPath` JAR | `pom.xml` points at `libs/postcode-io-gateway-client-1.0.0.jar`; `libs/` is excluded. | **Critical / P0 build:** a fresh clone cannot compile. | Publish/generate a versioned client from an owned contract; remove `systemPath`; verify clean-clone build and contract compatibility. | Postcode contract/publishing. | Yes | L |
| [LOC-02](https://github.com/jobseekercopilot/location-gateway/issues/2) | Implement or remove location-name search | **Remediated:** `/api/locations?q=` validates a bounded place name, requests at most ten results through a versioned postcode-gateway contract, and preserves stable redacted errors. | The broken advertised route is restored; the unversioned provider and absent browser suite remain documented risks. | Retain provider/consumer contract, validation, zero/multiple/error tests and deterministic cross-service smoke. | Postcode-io-gateway place endpoint and official provider capability. | No | M |
| [LOC-03](https://github.com/jobseekercopilot/location-gateway/issues/3) | Validate postcode input and map provider errors | Controller accepts unconstrained path text, catches every exception as 500 and includes exception text and postcode in response. | **High / P1 API/security:** invalid/not-found/provider errors are indistinguishable and internal details leak. | Canonicalise and validate UK postcode/outcode; map 400/404/429/502/503/504 to stable errors; avoid echoing unnecessary PII; test malformed JSON/content types. | Postcode error contract. | Yes | M |
| [LOC-04](https://github.com/jobseekercopilot/location-gateway/issues/4) | Bound the synchronous provider call | **Remediated:** the generated client has validated connect/read deadlines, bounded transient-response retry/backoff, a per-instance circuit and circuit-derived readiness. | The cascading thread-exhaustion risk is bounded; each instance may still observe failures before its local circuit opens. | Retain slow/outage/recovery tests and documented budgets; tune only from measured latency/capacity. | LOC-01, postcode SLO and repository-owned resilience policy. | No | M |
| [LOC-05](https://github.com/jobseekercopilot/location-gateway/issues/5) | Add cache/rate controls and operational signals | **Remediated:** successful canonical lookups use a TTL/LRU-bounded cache; direct callers have bounded in-memory throttling; safe provider/cache/rate metrics and beta alerts are defined. | The evidenced provider-waste/visibility risk is bounded; instance-local state and direct-caller aggregation are documented residual risks. | Retain normalization/TTL/size/rate/metric privacy tests; tune only from measured capacity and deploy metrics behind authenticated collection. | Existing Micrometer/Actuator approach. | No | M |
| [LOC-06](https://github.com/jobseekercopilot/location-gateway/issues/6) | Add meaningful contract/integration testing | Four tests cover a happy service mapping, controller shape and OpenAPI; no invalid/outage/rate/timeout/contract tests exist. | **High / P1 testing:** resilience/error semantics are unproven. | Add contract plus provider-stub integration tests for full postcode and all negative scenarios; include browser E2E. | LOC-01–05. | Yes | M |
| [LOC-07](https://github.com/jobseekercopilot/location-gateway/issues/7) | Harden container, readiness and docs | **Remediated:** aggregate circuit-derived readiness, graceful shutdown and a test-enforcing digest-pinned non-root image with blocking image scan and operations runbook are present. | The repository operational baseline is complete; private telemetry export/alert delivery and complete browser evidence remain external blockers. | Retain runtime/readiness/privacy tests and prove alert delivery in the controlled beta environment. | LOC-01 and repository-side LOC-02 behavior complete. | Yes | M |
| [LOC-08](https://github.com/jobseekercopilot/location-gateway/issues/8) | Establish reliable dependency vulnerability scanning | CI emits `mvn dependency:tree` but performs no vulnerability analysis; no dependable advisory-feed cache or risk-acceptance workflow is configured. | **High / P1 dependency:** vulnerable gateway or HTTP libraries can reach beta without a reliable blocking signal. | Select a proprietary-compatible Maven scanner, configure authenticated/cached advisory data, publish a machine-readable report, fail on unaccepted Critical/High findings and document the risk-acceptance process. | Platform CI and advisory-feed decision. | Yes | M |

## LOC-07 remediation evidence

- General health and downstream-circuit readiness are aggregate-only, contain
  no contributor details and never make a synthetic downstream request.
- Metrics are private by default; existing bounded signals and alerts remain
  documented in the location controls and downstream resilience contracts.
- Release validation runs the complete Maven suite before copying the verified
  JAR. The digest-pinned runtime runs read-only as `10001:10001`, is tested
  through downstream outage and graceful stop, and receives a blocking
  Critical/High OS and library image scan.
- README and the operations runbook record the real API/configuration,
  develop-only workflow, proprietary licence, ownership and residual risks.

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

At completion of LOC-04, LOC-02 and LOC-05 through LOC-07 remained open, so the
service was still **not beta-ready**.

## LOC-05 remediation evidence

- Successful lookups share one cache entry across canonical postcode variants;
  failures are not cached. TTL, entry count, fixed-window request count/window,
  and tracked-caller state are validated and bounded.
- Cached values are defensive copies. Cache state stores no caller association;
  rate state stores no postcode; neither postcode nor caller address appears in
  metric labels.
- Direct-caller throttling ignores spoofable forwarded headers and returns a
  stable `429` with `Retry-After`. Bounded LRU state prevents memory exhaustion.
- Low-cardinality cache/rate counters and provider outcome/latency/retry/circuit
  metrics are exposed through Actuator. The operational policy defines concrete
  beta alert thresholds, ownership, tuning constraints and residual risks.
- Unit/MVC tests cover TTL expiry, LRU eviction, defensive copies, canonical
  hits, failure misses, request rejection/window reset/state eviction, redacted
  metric tags, provider metrics and configuration safety bounds.

LOC-05 is complete and unblocks LOC-06. LOC-02, LOC-06 and LOC-07 remain open,
so the service is still **not beta-ready**.

## LOC-02 remediation evidence

- Postcode-io-gateway owns a bounded LIVE-mode `/api/places` boundary backed
  by the provider's documented place query; FIXTURE mode fails closed because
  no approved place dataset exists there.
- Both gateways enforce a 2–80 character Unicode place-name policy and a fixed
  maximum of ten results before provider access.
- The consumer contract maps only bounded place identity, display name,
  representative outcode, region, administrative district and coordinates;
  additive fields are accepted and incompatible responses fail closed.
- Search shares caller throttling, downstream deadlines, retry/circuit controls
  and low-cardinality provider telemetry. Query text is absent from logs,
  metric labels and redacted errors.
- The client BFF transaction log records only the operation and result count;
  focused tests prove successful and failed search terms are not logged.
- Focused provider/consumer tests cover normalization, URI query encoding,
  multiple/empty results, response compatibility, 429 retry, mode safety and
  stable public mapping. Cross-service evidence is recorded on the issue/PRs.

LOC-02 is complete and unblocks LOC-06 and LOC-07. LOC-06 and LOC-07 remain
open, so the service is still **not beta-ready**.

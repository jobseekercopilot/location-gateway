# Postcode gateway downstream resilience

The location gateway makes a synchronous, idempotent `GET` through its generated
postcode gateway client. The following defaults bound that dependency while
leaving enough time for the postcode gateway's documented four-second logical
provider budget:

| Control | Default | Purpose |
|---|---:|---|
| Connect timeout | 500 ms | Bound connection establishment |
| Read timeout | 5 s | Bound one downstream response |
| Maximum attempts | 2 | Permit at most one safe retry |
| Backoff | 100–250 ms | Prevent an immediate retry burst |
| Circuit threshold | 5 failed logical calls | Stop consuming request threads during an outage |
| Circuit open duration | 30 s | Delay before one half-open recovery probe |

Only explicit transient HTTP responses (`408`, `425`, `429`, and `5xx`) are
retried. `400` and `404` are caller/result outcomes and never affect the
circuit. Transport failures and read timeouts affect the circuit but are not
retried because repeating a blocking timeout would amplify request-thread use.
All retry attempts are for the generated idempotent GET only.

After the configured number of failed logical calls, the circuit rejects new
lookups locally. The existing public error handler maps an open circuit and
transport failure to stable redacted `503` behavior, timeouts to `504`, and
other unusable responses to `502`; no downstream body or submitted postcode is
returned.

## Readiness and operations

The `postcodeGateway` health contributor reports `OUT_OF_SERVICE` while the
circuit is within its open window. It does not make a synthetic downstream call.
After the open duration it reports `HALF_OPEN`; the next real request is the
single recovery probe. A successful probe closes the circuit, while a failed
probe reopens it.

Configuration is validated at startup. HTTP deadlines are limited to 1 ms–30 s,
backoff to 1 ms–5 s, the open window to 1 ms–10 minutes, attempts to 1–3, and
the failure threshold to 1–20. Tune values only against measured service latency
and capacity, retaining the rule that a slow transport call is not retried.

Focused tests cover a real loopback read timeout, transient retry/backoff,
non-retryable caller and transport failures, open rejection, half-open recovery,
readiness, unsafe configuration, and stable service error mapping. LOC-05 owns
cache/rate/metric work; LOC-06 owns the complete provider-stub and consumer
journey.

Residual risk: the circuit is per application instance and has in-memory state.
Readiness reflects observed calls, not an active provider probe, and an upstream
outage may affect up to the configured failure threshold on each instance before
the circuit opens.

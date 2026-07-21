# Location cache, rate and telemetry controls

These controls protect the postcode provider path without retaining caller and
postcode associations or creating high-cardinality telemetry.

## Successful-result cache

- Key: the validated uppercase postcode/outcode with all whitespace removed.
  Formatting variants therefore share one entry.
- Value: a defensive copy of the public location response. Caller identity is
  never stored with it.
- Policy: successful mappings only, 15-minute TTL, 10,000-entry per-instance
  least-recently-used bound by default. Invalid, not-found, throttled, timeout,
  outage, and malformed provider responses are never cached.
- Privacy: keys and values exist in process memory only and are not logged,
  persisted, or used as metric labels. A restart clears the cache.

Tune TTL only against provider change frequency and stale-location tolerance.
Tune size from observed working-set/memory evidence. Startup rejects a TTL
outside 1 second–24 hours or a bound outside 1–100,000 entries.

## Caller capacity limit

Each direct network caller is limited by the servlet connection's remote
address. Forwarded-IP headers are deliberately ignored because this service
does not yet have an approved trusted-proxy boundary. The default is 120
requests per one-minute fixed window with at most 20,000 tracked callers per
instance. Rejection returns stable JSON `429` and a whole-second `Retry-After`.

The state is bounded, in memory, per instance, and contains no postcode. It is
a provider-capacity control, not a distributed quota or authentication control.
In the current architecture the direct caller is normally the Express BFF, so
the BFF/edge must separately own any per-user abuse policy once its session
design is complete. Do not trust `X-Forwarded-For` here without a separately
approved proxy allow-list and spoofing tests.

## Metrics and beta alerts

The Actuator `metrics` endpoint exposes only low-cardinality dimensions:

- `location.postcode.cache.requests{result=hit|miss}` and
  `location.postcode.cache.evictions`
- `location.postcode.rate.requests{result=allowed|rejected}` and
  `location.postcode.rate.caller.evictions`
- `location.postcode.provider.requests{outcome=success|client_error|server_error|transport_error|invalid_response}`
  as a latency timer
- `location.postcode.provider.retries` and
  `location.postcode.provider.circuit.rejections`

No postcode, caller address, response body, or correlation ID is a metric tag.
For controlled beta, route these through the platform's authenticated metrics
collector and configure:

- warning when provider non-success ratio exceeds 5% for 10 minutes; critical
  above 20% for 5 minutes;
- warning when provider p95 latency exceeds 4 seconds for 10 minutes;
- warning on any sustained circuit rejection for 5 minutes;
- warning when caller rejection ratio exceeds 1% for 10 minutes, followed by
  capacity/abuse review rather than blindly increasing the limit;
- warning when cache evictions remain above 10% of misses for 15 minutes.

Page only on sustained provider unavailability or a critical error ratio.
Dashboard cache hit/miss, rate rejection, provider outcomes/latency/retry, and
circuit health together. Never expose the Actuator endpoint directly to the
public internet; LOC-07 owns the wider runtime/management hardening.

## Residual risks

Cache and limits are instance-local, so restarts reset state and multiple
instances do not share quotas or entries. Concurrent first misses can still
make duplicate calls. Remote-address limits aggregate users behind the same
direct caller. These trade-offs keep this beta control bounded and dependency
free; move to shared infrastructure only with measured need and an approved
availability/privacy design.

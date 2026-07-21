# Bounded place-name search

The public route used by the Express BFF is:

```text
GET /api/locations?q={place-name}
```

The gateway trims the query and collapses whitespace. Valid input contains
2–80 Unicode letters/marks, spaces, apostrophes, hyphens or periods. Control
characters, URI delimiters and other punctuation receive a stable redacted
`400`. The gateway always asks postcode-io-gateway for at most ten results.

Postcode-io-gateway owns the external Postcodes.io `/places` contract and
returns persistent place ID, primary name, representative outcode, region,
optional administrative district and coordinates. Location-gateway maps those
fields into its existing `LocationResponse`; zero matches is a successful empty
list. Query text is not logged or used in metric labels. Spring web/client
logging is held at INFO so a global debug flag cannot emit request query
strings; do not weaken those category levels in an environment handling user
queries.

Search shares the direct-caller rate limit, generated-client deadlines,
bounded transient retry, circuit breaker and provider outcome metrics used by
postcode lookup. Search results are not cached: place-name results can change
and the provider gateway does not yet own a measured search-cache policy. Add
one only with explicit freshness, memory and privacy evidence.

Automated provider tests use deterministic fixtures and never contact the live
provider. For a cross-service test, run postcode-io-gateway in LOCAL/LIVE mode
against a loopback provider stub, then point `POSTCODE_IO_GATEWAY_URL` at it.
The provider must be merged first because the checked-in consumer contract
records its reviewed revision.

Residual limitations: Postcodes.io places exclude Northern Ireland and its
endpoint is unversioned. The compatibility tests and failure metric detect
schema drift but cannot prevent an upstream change. Browser coverage remains
owned by CLIENT-07 after the client clean-build prerequisite.

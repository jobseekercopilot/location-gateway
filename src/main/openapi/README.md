# Postcode gateway consumer contract

`postcode-io-gateway.yaml` is the versioned consumer contract used to generate
the RestTemplate client during Maven's `generate-sources` phase. Generated Java
and binaries belong under `target/` and must not be committed.

The postcode contract records the source repository, exact reviewed revision
and exported contract checksum in its `x-source-*` fields. The Location Service
snapshot has the same evidence in `location-service.pin.json`. To update either:

1. review the postcode/place gateway controllers, response models and exported OpenAPI
   at the proposed `develop` revision;
2. update this consumer contract and `x-source-revision` together;
3. run `mvn -B clean verify` from a clean clone;
4. run the postcode contract test and the paired local gateway journey; and
5. review the generated API/model diff under `target/generated-sources` without
   committing it.

Breaking provider changes require coordinated consumer changes. Do not restore
the old `libs/*.jar` workflow or use Maven `systemPath` dependencies.

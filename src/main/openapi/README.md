# Postcode gateway consumer contract

`postcode-io-gateway.yaml` is the versioned consumer contract used to generate
the RestTemplate client during Maven's `generate-sources` phase. Generated Java
and binaries belong under `target/` and must not be committed.

The contract records the source repository and exact reviewed revision in its
`x-source-*` fields. To update it:

1. review the postcode gateway controller, response model and exported OpenAPI
   at the proposed `develop` revision;
2. update this consumer contract and `x-source-revision` together;
3. run `mvn -B clean verify` from a clean clone;
4. run the postcode contract test and the paired local gateway journey; and
5. review the generated API/model diff under `target/generated-sources` without
   committing it.

Breaking provider changes require coordinated consumer changes. Do not restore
the old `libs/*.jar` workflow or use Maven `systemPath` dependencies.

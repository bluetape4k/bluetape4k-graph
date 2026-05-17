# Central Release POM Metadata

## Context

The 0.3.0 Central Portal release failed validation because graph POMs still
referenced `io.github.bluetape4k:*:1.8.0-SNAPSHOT`.

## Decision

Use the released `io.github.bluetape4k:bluetape4k-bom:1.8.0` version for graph
release dependencies.

## Outcome

Generated publication POMs now contain `1.8.0` bluetape4k dependency metadata
and no `SNAPSHOT` references.

## Verification

- `./gradlew generatePomFileForBluetapeGraphPublication --no-daemon --no-configuration-cache --no-build-cache`
- Searched generated `pom-default.xml` files for `SNAPSHOT`.

## Future Guidance

Release branches must not reference bluetape4k `-SNAPSHOT` coordinates in
catalog versions, direct dependency versions, or generated POM dependency
management.

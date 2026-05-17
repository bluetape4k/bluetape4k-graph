# Non-published module BOM filter

## Context

Graph benchmark modules were discoverable as Gradle projects and could be
accidentally managed or published with library modules.

## Decision

Apply one non-published module filter to BOM constraints, NMCP setup,
publication/signing setup, aggregation, and coverage registration.

## Outcome

Graph examples and benchmark modules are excluded from generated BOM metadata
and Central Portal aggregation.

## Verification

- `./gradlew clean generatePomFileForBluetapeGraphPublication --no-daemon --no-configuration-cache --no-build-cache`
- Generated BOM POM scan found no `examples`, `demo`, or `benchmark` entries.

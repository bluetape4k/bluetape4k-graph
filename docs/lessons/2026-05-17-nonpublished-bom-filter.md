# 미게시 module BOM filter

## 맥락

Graph benchmark modules were discoverable as Gradle projects and could be
accidentally managed or published with library modules.

## 결정

Apply one non-published module filter to BOM constraints, NMCP setup,
publication/signing setup, aggregation, and coverage registration.

## 결과

Graph examples and benchmark modules are excluded from generated BOM metadata
and Central Portal aggregation.

## 검증

- `./gradlew clean generatePomFileForBluetapeGraphPublication --no-daemon --no-configuration-cache --no-build-cache`
- Generated BOM POM scan found no `examples`, `demo`, or `benchmark` entries.

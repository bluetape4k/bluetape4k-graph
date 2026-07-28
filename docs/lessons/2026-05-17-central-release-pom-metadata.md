# Central Release POM 메타데이터

## 맥락

The 0.3.0 Central Portal release failed validation because graph POMs still
referenced `io.github.bluetape4k:*:1.8.0-SNAPSHOT`.

## 결정

Use the released `io.github.bluetape4k:bluetape4k-bom:1.8.0` version for graph
release dependencies.

## 결과

Generated publication POMs now contain `1.8.0` bluetape4k dependency metadata
and no `SNAPSHOT` references.

## 검증

- `./gradlew generatePomFileForBluetapeGraphPublication --no-daemon --no-configuration-cache --no-build-cache`
- Searched generated `pom-default.xml` files for `SNAPSHOT`.

## 향후 지침

Release branches must not reference bluetape4k `-SNAPSHOT` coordinates in
catalog versions, direct dependency versions, or generated POM dependency
management.

## 2026-07-17 Follow-up

A repository-wide snapshot audit found that this rule must be executable rather
than a manual release check. Published BOM imports now use versioned central
`bt4k` aliases, and `scripts/publication/validate_poms.rb` checks every
generated POM both structurally and through Maven effective-model construction.
Regular dependencies may remain versionless only when Maven can resolve them
through the same POM's dependency management or a versioned imported BOM.

# bt4k Version Catalog Consumption

## Context

`bluetape4k-graph` duplicated shared dependency versions in its local catalog
even though the ecosystem catalog already owns those values.

## Decision

Import `io.github.bluetape4k:bluetape4k-version-catalog` as `bt4k` and resolve
shared leaf dependency constraints through `bt4kVersion(alias)`. Keep local
aliases and plugin/BOM train versions where this repository still has local
build-script requirements.

## Outcome

Selected direct dependency aliases are versionless locally, and the build
script reads their managed versions from `bt4k`. Duplicate generated
constraints were removed before verification.

## Verification

- `git diff --check`
- `./gradlew help --no-daemon --no-configuration-cache`
- `./gradlew compileKotlin --no-daemon --no-configuration-cache`

## Future Guidance

Do not reintroduce local pins for common graph/runtime dependencies already
published by `bluetape4k-dependencies`; migrate the remaining plugin/BOM train
duplicates only with a dedicated build-script cleanup.

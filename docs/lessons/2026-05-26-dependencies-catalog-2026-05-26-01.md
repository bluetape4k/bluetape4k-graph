# Dependencies Catalog 2026-05-26-01

## 맥락

`bluetape4k-dependencies` published `catalog/2026-05-26-01` with centralized security dependency lines.

## 결정

Update the downstream default `bluetape4kDependenciesCatalogRef` to the new catalog tag instead of pinning shared external library versions locally.

## 결과

The repository now resolves shared dependency versions from `catalog/2026-05-26-01` by default.

## 검증

Checked the catalog ref in `settings.gradle.kts`.

## 향후 메모

For shared external libraries, update `bluetape4k-dependencies` first, tag the catalog, then move downstream repositories to that tag.

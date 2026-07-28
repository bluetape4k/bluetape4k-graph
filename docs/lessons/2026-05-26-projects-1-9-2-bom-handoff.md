# Projects 1.9.2 BOM handoff

## 맥락

`bluetape4k-projects` 1.9.2 was released and `bluetape4k-bom:1.9.2` is visible
from Maven Central.

## 결정

Use the stable `bluetape4k-bom` 1.9.2 line for this release-prep branch instead
of the matching projects snapshot.

## 결과

The version catalog now resolves `io.github.bluetape4k:bluetape4k-bom` from the
stable 1.9.2 release while leaving this repository's own release line unchanged.

## 검증

- Maven Central HTTP 200 for `bluetape4k-bom:1.9.2`
- `./gradlew help --refresh-dependencies --no-daemon --no-configuration-cache --no-build-cache`

# Issue #127 Ktor API Hygiene Audit

## Context

Issue #127 is a periodic audit for deprecated or internal Ktor APIs in `graph-ktor` and
`ktor-graph-examples`.

## Decision

No code change is required for this audit. The repository already uses Ktor BOM `3.5.0`, which Maven
Central metadata reports as the latest stable 3.x release, and the affected modules compile without Ktor
deprecation warnings.

## Outcome

Kept the implementation unchanged and recorded the verification evidence. The issue can close as a
verification-only maintenance item.

## Verification

- Maven Central `io.ktor:ktor-bom` metadata reports `<latest>3.5.0</latest>` and `<release>3.5.0</release>`.
- `gradle/libs.versions.toml` already sets `ktor = "3.5.0"`.
- `./gradlew :bluetape4k-graph-ktor:build :ktor-graph-examples:build --warning-mode=all --console=plain --no-daemon` passed.
- The same build output showed zero `@Deprecated` or Ktor API deprecation warnings.
- `BackendGraphPluginRuntimeTest` passed 4 tests.
- `GraphPluginTest` passed 6 tests.
- `ktor-graph-examples` passed 4 tests.

## Future Guard

When an issue body mentions `:graph-ktor`, verify the current Gradle project name first. The active project
path is `:bluetape4k-graph-ktor`, while the source directory remains `ktor/graph-ktor`.

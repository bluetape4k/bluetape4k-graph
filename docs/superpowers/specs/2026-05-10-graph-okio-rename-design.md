# graph-okio Rename Design

## Context

Issue #76 requests renaming the Gradle module previously published as `graph-io-okio` to `graph-okio`.
The implementation package remains `io.bluetape4k.graph.io.okio`; this keeps source compatibility for callers while changing the Gradle project and Maven artifact identity.

## Scope

- Register `graph-io/okio` as Gradle project `:graph-okio`.
- Remove automatic registration of `graph-io/okio` as `:graph-io-okio`.
- Update in-repo project dependencies, CI workflows, BOM documentation, and module README dependency snippets.
- Keep the physical directory `graph-io/okio` and Kotlin package names unchanged.

## Non-Goals

- No source package rename.
- No public class or function rename.
- No format, compression, or OkIO runtime behavior change.
- No compatibility shim module named `graph-io-okio`; the issue asks for a module rename, and keeping both Gradle projects would continue publishing the old artifact.

## Acceptance Criteria

- `./gradlew projects` lists `:graph-okio` and does not list `:graph-io-okio`.
- `./gradlew :graph-okio:test --no-configuration-cache` succeeds.
- `./gradlew :graph-io-benchmark:compileKotlin --no-configuration-cache` succeeds.
- GitHub Actions references use `:graph-okio`.
- README dependency snippets use `io.github.bluetape4k.graph:graph-okio`.

## Review Notes

- Security: rename only; no parsing, credentials, or network behavior changes.
- Ops/SRE: CI task names must be updated so fast feedback still runs OkIO tests and coverage.
- Structural: Gradle registration must avoid duplicate projectDir mappings for `graph-io/okio`.
- Performance: no runtime code path changes.

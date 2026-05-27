# Issue 241 Suspend Test Harness

## Context

The 0.4.2 pre-release repository scan found remaining suspend tests using
`runTest` around real graph IO and Testcontainers-backed graph operations.

## Decision

Use `runSuspendIO` for real IO and backend-backed suspend tests, and keep
bluetape4k assertion helpers for exception checks.

## Outcome

The Okio graph-io suspend tests and code-graph example suspend tests now use
the same coroutine test harness policy as the rest of the 0.4.2 release line.

## Verification

- `./gradlew :bluetape4k-graph-okio:test --tests '*SuspendAdapterTest' --tests '*CsvOkioExtensionsTest' --tests '*GraphMLOkioExtensionsTest' --tests '*JacksonOkioExtensionsTest' :code-graph-examples:test --tests '*CodeGraphSuspendTest'`
- Pattern scan for `runTest`, `kotlin.test`, JUnit assertion imports, and
  AssertJ-style assertions in touched test paths.
- `git diff --check`

## Future Notes

Before releasing a milestone, scan example modules as well as library modules
for coroutine harness drift; example Testcontainers tests can hide the same
`runTest` risk as production module tests.

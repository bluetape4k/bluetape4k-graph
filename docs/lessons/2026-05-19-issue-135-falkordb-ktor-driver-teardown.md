# Issue #135 FalkorDB Ktor Driver Teardown

## Context

`FalkorDBKtorGraphAppTest` creates a shared FalkorDB driver for the whole PER_CLASS test lifecycle. The
`falkorDbModule(driver)` contract says the caller owns the driver and must close it, but the test did not close
the companion-object driver.

## Decision

Keep the shared driver so the Testcontainers-backed Ktor example avoids per-test connection pool creation, and close
it once in `@AfterAll` after all test methods complete.

## Outcome

Added a PER_CLASS teardown that closes the caller-owned FalkorDB driver only when the lazy fixture was initialized,
and updated the test KDoc so the lifecycle contract is explicit.

## Verification

- `./gradlew :ktor-graph-examples:compileTestKotlin :ktor-graph-examples:test --tests "*.FalkorDBKtorGraphAppTest" --console=plain --no-daemon` passed.
- `FalkorDBKtorGraphAppTest` passed 2 tests.
- IntelliJ diagnostics were unavailable for this worktree because the IDE MCP did not have the worktree opened as a project; Gradle compile/test was used as the fallback.

## Future Guard

When Ktor example tests accept caller-owned drivers or clients, keep the shared fixture at class scope and close it in
`@AfterAll`. Do not rely on `testApplication` teardown to close resources owned outside the application module.

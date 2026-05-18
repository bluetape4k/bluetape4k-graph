# WIP - bluetape4k-graph

Snapshot: 2026-05-19 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 6 issues; 5 remain after #134 closes through this work.

## Refresh Notes

Verified with `gh` on 2026-05-19 KST.

- qmd was queried first for prior graph lessons, specs, plans, and follow-ups.
- Existing issues #156, #157, and #158 were unassigned; they are now assigned to `debop`.
- New issue registered from this audit:
  - [#160](https://github.com/bluetape4k/bluetape4k-graph/issues/160) - `bug: AGE, Memgraph, and TinkerGraph suspendTransaction still bridge through runBlocking`
- PR #159 (`chore: refresh WIP snapshot - 2026-05-18`) is already merged, so this file reflects the current post-merge GitHub state.
- Pre-existing local change `gradlew.bat` was not touched.
- Issues #18 and #19 are handled by the CI quality-gate and dependency-governance refresh:
  - CI now blocks on Detekt in the PR build job while Kover remains report-only.
  - Leaf Dependabot stays scoped to GitHub Actions; Gradle/Maven library updates remain centralized in `bluetape4k-dependencies`.
- Issue #126 is handled by adding a full-nightly Spring Boot FalkorDB Testcontainers step and a gated
  `@SpringBootTest` that verifies the auto-configuration path against a live FalkorDB container.
- Issue #158 is handled by moving Neo4j suspend transactions to the reactive transaction API, keeping
  rollback/cleanup semantics, and materializing returned transaction `Flow` values before commit.
- Issue #160 is handled by removing the remaining AGE, Memgraph, and TinkerGraph `runBlocking`
  transaction bridges and adding cancellation rollback plus returned `Flow` materialization tests.
- Issue #156 is handled by rethrowing `CancellationException` from FalkorDB suspend `graphExists()`
  while preserving the ordinary driver-failure fallback.
- Issue #157 is handled by replacing FalkorDB/Memgraph schema manager `runCatching {}` ignore
  helpers with explicit `try/catch` branches that rethrow `CancellationException` before expected
  already-exists or missing-resource fallbacks.
- Issue #111 is handled by adding graph-io CSV sample dataset loaders, bundled fixtures, sync/suspend
  TinkerGraph smoke coverage, English/Korean README import flows, and release notes.
- Issue #127 is handled by auditing `graph-ktor` and `ktor-graph-examples` against Ktor 3.5.0:
  the latest stable 3.x BOM is already used, compile output has zero Ktor deprecation warnings, and
  the targeted Ktor plugin/example tests pass without code changes.
- Issue #135 is handled by closing the caller-owned FalkorDB driver in
  `FalkorDBKtorGraphAppTest` after the PER_CLASS test lifecycle completes.
- Issue #133 is handled by adding the FalkorDB-backed Ktor `GraphPlugin` smoke example to the
  English and Korean README example module tables.
- Issue #134 is handled by converting the public `GraphFalkorDBAutoConfiguration` KDoc blocks
  for driver, operations, virtual-thread adapter, and health indicator beans to English.

## Current Direction

0.3.0 is released. The next work should stabilize coroutine/cancellation
contracts and backend readiness before widening examples or benchmark lanes:

1. Return to Neptune testability research (#113) before the backlog backend epic (#30).
2. Keep documentation-only cleanup behind backend-readiness blockers unless it reduces migration risk.
3. Keep benchmark work behind stable backend readiness and CI signal quality.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#113](https://github.com/bluetape4k/bluetape4k-graph/issues/113) Neptune local testability research | M | Required predecessor for #30; backlog milestone. |
| P3 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Blocked on #113. |
| P4 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | After CI gates settle. |
| P4 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After stable baselines. |
| P4 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | After merged #40 baseline. |

## Dependency Map

```text
#156 FalkorDBGraphSuspendOperations.graphExists() CancellationException fix
  -> suspend graphExists now rethrows coroutine cancellation
#157 FalkorDB/MemgraphGraphSchemaManager broad runCatching fix
  -> correctness baseline for graph-falkordb and graph-memgraph

#158 Neo4j suspendTransaction runBlocking bridge
#160 AGE / Memgraph / TinkerGraph suspendTransaction runBlocking bridge
  -> suspend-to-blocking transaction bridges removed consistently
  -> cancellation rollback and returned Flow materialization covered by tests

#113 Neptune local testability research
  -> #30 Neptune backend

#18 CI quality gates
#19 dependency automation

#111 graph-io backed sample dataset loaders
  -> improved examples onboarding

#127 Ktor API hygiene audit
  -> no Ktor deprecation or version work needed on 3.5.0

#135 FalkorDB Ktor example teardown
  -> caller-owned FalkorDB driver is closed after PER_CLASS test lifecycle

#133 FalkorDB Ktor README discoverability
  -> English/Korean README tables list FalkorDBKtorGraphAppTest

#134 FalkorDB auto-configuration KDoc language
  -> public KDoc blocks are English and document bean registration contracts
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Cancellation correctness | 1 | #157 merged; idle unless a new cancellation bug appears. |
| Suspend transaction safety | 1 | #160 merged; keep this lane idle unless a new transaction-safety issue appears. |
| Research / backend readiness | 1 | `#113` before `#30`. |
| CI / automation | 1 | Keep Detekt/Kover/Dependabot governance stable; open focused follow-up issues for new gates. |
| Examples / benchmarks | 1 | #111 merged; idle unless another adoption issue is selected. |

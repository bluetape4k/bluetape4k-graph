# WIP - bluetape4k-graph

Snapshot: 2026-05-18 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 17 issues.

## Refresh Notes

Verified with `gh` on 2026-05-18 KST.

- qmd was queried first for prior graph lessons, specs, plans, and follow-ups.
- Existing issues #156, #157, and #158 were unassigned; they are now assigned to `debop`.
- New issue registered from this audit:
  - [#160](https://github.com/bluetape4k/bluetape4k-graph/issues/160) - `bug: AGE, Memgraph, and TinkerGraph suspendTransaction still bridge through runBlocking`
- PR #159 (`chore: refresh WIP snapshot - 2026-05-18`) is already merged, so this file reflects the current post-merge GitHub state.
- Pre-existing local change `gradlew.bat` was not touched.

## Current Direction

0.3.0 is released. The next work should stabilize coroutine/cancellation
contracts and backend readiness before widening examples or benchmark lanes:

1. Fix cancellation and `runCatching{}` issues in FalkorDB/Memgraph (#156/#157).
2. Remove `runBlocking` bridges from suspend transaction implementations (#158/#160).
3. Complete Neptune testability research (#113) before starting the backend epic (#30).
4. Resume examples, CI automation, and benchmarks only after correctness baselines are stable.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#156](https://github.com/bluetape4k/bluetape4k-graph/issues/156) FalkorDBGraphSuspendOperations.graphExists() swallows CancellationException | S | Replace `runCatching{}` so suspend function propagates cancellation correctly. |
| P1 | [#157](https://github.com/bluetape4k/bluetape4k-graph/issues/157) FalkorDB/MemgraphGraphSchemaManager overly broad runCatching{} | S | Narrow to expected exceptions; fix correctness baseline for graph-falkordb and graph-memgraph. |
| P1 | [#158](https://github.com/bluetape4k/bluetape4k-graph/issues/158) Neo4jGraphSuspendOperations.suspendTransaction() runBlocking inside withContext(IO) | M | Remove `runBlocking`; use async Neo4j driver or coroutine-safe bridge to prevent IO thread starvation. |
| P1 | [#160](https://github.com/bluetape4k/bluetape4k-graph/issues/160) AGE/Memgraph/TinkerGraph suspendTransaction runBlocking bridge | M | Align the remaining suspend transaction implementations with #158. |
| P1 | [#113](https://github.com/bluetape4k/bluetape4k-graph/issues/113) Neptune local testability research | M | Required predecessor for #30. |
| P2 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Deferred from 0.3.0. |
| P2 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot / Renovate automation | S | Deferred from 0.3.0. |
| P2 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Blocked on #113. |
| P2 | [#111](https://github.com/bluetape4k/bluetape4k-graph/issues/111) graph-io backed sample dataset loaders | M | Useful after backend readiness is clear. |
| P3 | [#126](https://github.com/bluetape4k/bluetape4k-graph/issues/126) add Spring Boot FalkorDB auto-config to nightly CI coverage | S | CI/documentation lane. |
| P3 | [#127](https://github.com/bluetape4k/bluetape4k-graph/issues/127) replace deprecated/internal Ktor APIs in graph-ktor | S | Build maintenance when Ktor flags concrete drift. |
| P3 | [#133](https://github.com/bluetape4k/bluetape4k-graph/issues/133) add FalkorDB Ktor example to README table | S | Documentation lane. |
| P3 | [#134](https://github.com/bluetape4k/bluetape4k-graph/issues/134) convert GraphFalkorDBAutoConfiguration KDoc to English | S | Documentation lane. |
| P3 | [#135](https://github.com/bluetape4k/bluetape4k-graph/issues/135) close FalkorDB driver in Ktor test teardown | S | Test hygiene. |
| P4 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | After CI gates settle. |
| P4 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After stable baselines. |
| P4 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | After merged #40 baseline. |

## Dependency Map

```text
#156 FalkorDBGraphSuspendOperations.graphExists() CancellationException fix
#157 FalkorDB/MemgraphGraphSchemaManager broad runCatching fix
  -> correctness baseline for graph-falkordb and graph-memgraph

#158 Neo4j suspendTransaction runBlocking bridge
#160 AGE / Memgraph / TinkerGraph suspendTransaction runBlocking bridge
  -> remove suspend-to-blocking transaction bridges consistently
  -> verify cancellation does not pin IO workers indefinitely

#113 Neptune local testability research
  -> #30 Neptune backend

#18 CI quality gates
#19 dependency automation

#111 graph-io backed sample dataset loaders
  -> improved examples onboarding
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Cancellation correctness | 1 | Start with `#156`, then `#157`. |
| Suspend transaction safety | 1 | `#158` and `#160`; fix strategy should be consistent across backends. |
| Research / backend readiness | 1 | `#113` before `#30`. |
| CI / automation | 1 | `#18` or `#19`, one at a time. |
| Examples / benchmarks | 1 | `#111` before benchmarks; keep benchmark work after CI is stable. |

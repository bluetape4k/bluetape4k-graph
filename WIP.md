# WIP - bluetape4k-graph

Snapshot: 2026-05-18 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 8 issues.

## Recently Completed

### 0.3.0 Release (2026-05-17)

- `#118` graph-okio README English rewrite, `#121` CHANGELOG 0.3.0 finalization,
  `#122` graph-ktor KDoc English → closed by PR #129.
- `#123` FalkorDB Ktor example → closed by PR #130.
- `#125` Spring Boot FalkorDB health indicator test + README → closed by PR #131.
- `#124` Pre-release smoke test gate (Nightly `scope=full`) → passed and closed.
- `#128` Release 0.3.0 Epic → closed.
- `#120` Version bump 0.3.0-SNAPSHOT → 0.3.0 (this PR).

### Earlier

- `#13` transaction DSL, `#32` schema/index manager, `#34` merge/upsert, and
  `#33` batch insert are merged.
- `#75` graph-core capability docs closed by PR #97.
- `#40` weighted path suspend tests closed by PR #98.
- `#96` graph-ktor module closed by PR #100, with CI/Nightly coverage added by
  PR #102 and preserved design docs added by PR #105.
- `#99` graph-spring-boot module naming closed by PR #106.
- `#17` build cache optimization closed by PR #107.
- `#16` public API KDoc examples closed by PR #109.
- `#10` domain example modules closed by PR #110.
- Example build/test coverage was split into the dedicated daily `Examples`
  workflow by PR #112.
- `#49` graph-okio DAEAD encrypted streaming closed by PR #114, with PR #115
  closing the post-review gaps.

## Current Direction

0.3.0 is released. The 0.3.1 queue focuses on CI/governance improvements and
example adoption. Neptune (#30) remains blocked until local testability is
proven (#113).

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#156](https://github.com/bluetape4k/bluetape4k-graph/issues/156) FalkorDBGraphSuspendOperations.graphExists() swallows CancellationException | S | Replace `runCatching{}` so suspend function propagates cancellation correctly. |
| P1 | [#158](https://github.com/bluetape4k/bluetape4k-graph/issues/158) Neo4jGraphSuspendOperations.suspendTransaction() runBlocking inside withContext(IO) | M | Remove `runBlocking`; use async Neo4j driver or coroutine-safe bridge to prevent IO thread starvation. |
| P1 | [#113](https://github.com/bluetape4k/bluetape4k-graph/issues/113) Neptune local testability research | M | Required predecessor for `#30`. |
| P1 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Deferred from 0.3.0. |
| P1 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot / Renovate automation | S | Deferred from 0.3.0. |
| P2 | [#157](https://github.com/bluetape4k/bluetape4k-graph/issues/157) FalkorDB/MemgraphGraphSchemaManager overly broad runCatching{} | S | Narrow to expected exceptions; fix correctness baseline for graph-falkordb and graph-memgraph. |
| P2 | [#111](https://github.com/bluetape4k/bluetape4k-graph/issues/111) graph-io backed sample dataset loaders | M | Deferred from 0.3.0. |
| P2 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Blocked on `#113`. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | After CI gates settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After stable baselines. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | After merged `#40` baseline. |

## Dependency Map

```text
#156 FalkorDBGraphSuspendOperations.graphExists() CancellationException fix (P1)
#157 FalkorDB/MemgraphGraphSchemaManager overly broad runCatching{} (P2)
  -> correctness baseline for graph-falkordb and graph-memgraph

#158 Neo4jGraphSuspendOperations.suspendTransaction() runBlocking inside withContext(IO) (P1)
  -> IO thread starvation under concurrent transaction load
  -> long-term: migrate to async Neo4j driver

#113 Neptune local testability research
  -> #30 Neptune backend

#18 CI quality gates
#19 dependency automation

#111 graph-io backed sample dataset loaders
  -> improved examples onboarding

#40 weighted path suspend tests (closed)
  -> #41 weighted path benchmark
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Research / backend readiness | 1 | `#113` before `#30`. |
| CI / automation | 1 | `#18` or `#19`, one at a time. |
| Backend expansion | 1 | `#30` only after `#113`. |
| Examples / adoption | 1 | `#111`. |
| Benchmarks | 1 | `#14`, `#15`, or `#41` after CI green. |

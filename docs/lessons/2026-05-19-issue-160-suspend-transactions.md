# 이슈 160 Suspend Transaction Cleanup

## 맥락

AGE, Memgraph, and TinkerGraph suspend transaction implementations still executed user suspend blocks through a blocking coroutine bridge inside a synchronous transaction callback.

## 결정

- Use backend-native suspend/reactive transaction boundaries where available.
- Materialize any returned transaction `Flow` before commit/transaction exit.
- For TinkerGraph, keep the in-memory rollback-snapshot semantics and guard the suspend and synchronous transaction paths with one shared semaphore gate.

## 결과

- AGE `suspendTransaction` now runs inside `newSuspendedTransaction(Dispatchers.IO)` with a dedicated current-transaction suspend scope.
- Memgraph now mirrors the Neo4j reactive transaction pattern with reactive commit/rollback/cleanup.
- TinkerGraph no longer invokes a blocking coroutine bridge, restores snapshots on failure or cancellation, and serializes synchronous and suspend transactions around rollback snapshots.

## 검증

- `./gradlew :bluetape4k-graph-tinkerpop:compileKotlin :bluetape4k-graph-tinkerpop:compileTestKotlin :bluetape4k-graph-tinkerpop:test --tests "io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperationsTest" :bluetape4k-graph-tinkerpop:detekt --console=plain --no-daemon`
- `./gradlew :bluetape4k-graph-tinkerpop:test :bluetape4k-graph-age:test :bluetape4k-graph-memgraph:test --console=plain --no-daemon`
- `./gradlew :bluetape4k-graph-age:detekt :bluetape4k-graph-memgraph:detekt :bluetape4k-graph-tinkerpop:detekt --console=plain --no-daemon`
- `rg -n "runBlocking" graph/graph-age graph/graph-memgraph graph/graph-tinkerpop -g '*.kt'` returned no matches.
- `git diff --check`
- `codex review --uncommitted` reported no actionable correctness issues after fixing a TinkerGraph gate-acquisition cancellation race found in the first pass.
- Claude CLI advisor was attempted twice but produced no output before timeout; record this as a review-tool availability gap, not a code finding.

## 향후 지침

When a transaction block can return `Flow`, collect it before committing or leaving the transaction. Lazy collection after transaction cleanup can turn a correct rollback/commit implementation into a closed-resource bug.

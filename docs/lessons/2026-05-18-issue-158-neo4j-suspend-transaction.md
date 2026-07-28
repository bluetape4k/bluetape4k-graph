# 레슨: Neo4j suspend transaction bridge removal

Date: 2026-05-18
Issue: [#158](https://github.com/bluetape4k/bluetape4k-graph/issues/158)

## 맥락

`Neo4jGraphSuspendOperations.suspendTransaction()` executed a suspend transaction block through
`runBlocking` inside an IO-dispatched synchronous transaction. That pinned an IO worker for the
whole user block and could starve concurrent coroutine workloads.

## 결정

Use Neo4j reactive transactions for the suspend transaction path instead of adapting the synchronous
transaction callback. Keep rollback, transaction close, and session close cleanup under
`NonCancellable`, and suppress cleanup failures onto the original failure.

## 결과

The suspend transaction block no longer bridges through `runBlocking`. Returned top-level `Flow`
results from transaction scope queries are materialized before commit and re-wrapped as in-memory
flows so callers do not receive a flow backed by a closed transaction.

## 검증

- `./gradlew :bluetape4k-graph-neo4j:compileKotlin :bluetape4k-graph-neo4j:compileTestKotlin :bluetape4k-graph-neo4j:detekt :bluetape4k-graph-neo4j:test --console=plain --no-daemon`
  - Result: 106 tests passing.
- Claude CLI review: P0/P1 none.
- Codex CLI review: P0/P1 none after fixing returned-Flow materialization and close-suppression findings.

## 향후 지침

When removing similar bridges for #160, check whether each backend can provide a true suspend-aware
transaction. If an interface method returns `Flow` from a transaction scope, verify whether the flow
is collected before commit or deliberately materialized before returning.

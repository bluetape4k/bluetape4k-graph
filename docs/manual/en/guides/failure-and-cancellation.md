# Failure and cancellation

Classify failures before retrying: validation, unsupported capability, connectivity, backend query/schema, transaction, codec/input, security/authentication, or cancellation.

`transaction {}` and `suspendTransaction {}` commit only after normal block completion and roll back on exceptions. Unsupported implementations throw instead of using auto-commit. Source contracts: [`GraphTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

Coroutine cancellation must reach the backend boundary and leave no later commit. Some suspend transaction implementations materialize a returned `Flow` before commit; verify this behavior in the selected backend, for example [`Neo4jGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

Batch default implementations can leave earlier items after a mid-batch failure. Importers can likewise report partial progress. Before retry, inspect durable counts and use a tested idempotency/merge key. For OkIO, wrong associated data, truncation, decompression limits, and atomic-write cleanup are deliberate failures, not retryable database errors: [`GraphIoOkioPathsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt).

## Reproduce and classify

```kotlin
val before = ops.countVertices("Person")
val failure = runCatching {
    ops.transaction {
        createVertex("Person", mapOf("requestId" to "r-42"))
        error("injected")
    }
}.exceptionOrNull()
check(failure != null && ops.countVertices("Person") == before)
```

For coroutine code, cancel the job while inside `suspendTransaction`; expected observation is propagated cancellation and unchanged durable count. For a plain batch/import, do not expect the same atomicity: inspect report phase and counts. Retry only connectivity/timeouts that the backend marks transient and only with a verified idempotency key. Validation, unsupported capability, authentication failure, corrupted ciphertext, and size-limit failures require input/configuration repair.

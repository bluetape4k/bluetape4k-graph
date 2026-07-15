# Failure and cancellation

Classify failures before retrying: validation, unsupported capability, connectivity, backend query/schema, transaction, codec/input, security/authentication, or cancellation.

`transaction {}` and `suspendTransaction {}` commit only after normal block completion and roll back on exceptions. Unsupported implementations throw instead of using auto-commit. Source contracts: [`GraphTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

Coroutine cancellation must reach the backend boundary and leave no later commit. Some suspend transaction implementations materialize a returned `Flow` before commit; verify this behavior in the selected backend, for example [`Neo4jGraphSuspendOperationsTest.kt`](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

Batch default implementations can leave earlier items after a mid-batch failure. Importers can likewise report partial progress. Before retry, inspect durable counts and use a tested idempotency/merge key. For OkIO, wrong associated data, truncation, decompression limits, and atomic-write cleanup are deliberate failures, not retryable database errors: [`GraphIoOkioPathsTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt).

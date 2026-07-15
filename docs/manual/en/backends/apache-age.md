# Apache AGE

Apache AGE is the choice when graph data must live inside an existing PostgreSQL operational boundary. Queries cross a Cypher-over-SQL layer, so JDBC connection state, graph context, PostgreSQL transactions, and AGE types are part of diagnosis.

The synchronous and coroutine adapters are [`AgeGraphOperations.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt) and [`AgeGraphSuspendOperations.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt). Transaction wiring is visible in [`JdbcTransactionExtensions.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/JdbcTransactionExtensions.kt), with rollback/cancellation evidence in [`AgeGraphSuspendOperationsTest.kt`](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt).

Do not assume every common schema operation maps safely to AGE. Inspect [`AgeGraphSchemaManager.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt) and its [tests](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManagerTest.kt). Verify with the `apache/age:PG16_latest` test fixture, then repeat against the production PostgreSQL/AGE combination.

Watch database connections, locks, SQL/Cypher error detail, query plans, and rollback counts. When a failure appears only after pooled connection reuse, inspect session initialization and graph selection before changing domain queries.

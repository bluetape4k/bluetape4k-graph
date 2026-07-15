# Neo4j and Memgraph

Both modules use Neo4j-driver-compatible Bolt and Cypher, so they share much application code. They remain separate backends because server behavior, supported Cypher, schema DDL, deployment, and operational signals differ.

Choose Neo4j when Neo4j is already operated or its transaction/schema behavior is the reference requirement. Start at [`Neo4jGraphOperations.kt`](../../../../graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt), then verify batch, merge, schema, and suspend transaction behavior in the neighboring [tests](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

Choose Memgraph when Memgraph is already deployed and its streaming/in-memory operational model fits the workload. The adapter is [`MemgraphGraphOperations.kt`](../../../../graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt); schema behavior is explicit in [`MemgraphGraphSchemaManager.kt`](../../../../graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt) and its [tests](../../../../graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManagerTest.kt).

Run the container tests against the exact server line used in deployment. Observe connection-pool saturation, transaction retries/rollback, query latency, and server logs. A query passing on one server is not proof that its schema statements or edge cases are portable to the other.

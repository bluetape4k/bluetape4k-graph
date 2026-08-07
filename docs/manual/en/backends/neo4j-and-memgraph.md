# Neo4j and Memgraph

![Backend decision map](../../assets/backends/backend-decision-map.png)

Both modules use Neo4j-driver-compatible Bolt and Cypher, so they share much application code. They remain separate backends because server behavior, supported Cypher, schema DDL, deployment, and operational signals differ.

Choose Neo4j when Neo4j is already operated or its transaction/schema behavior is the reference requirement. Start at [`Neo4jGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt), then verify batch, merge, schema, and suspend transaction behavior in the neighboring [tests](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

Choose Memgraph when Memgraph is already deployed and its streaming/in-memory operational model fits the workload. The adapter is [`MemgraphGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt); schema behavior is explicit in [`MemgraphGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt) and its [tests](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManagerTest.kt).

Run the container tests against the exact server line used in deployment. Observe connection-pool saturation, transaction retries/rollback, query latency, and server logs. A query passing on one server is not proof that its schema statements or edge cases are portable to the other.

## Configure and run the same operation

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j") // or ...-memgraph
}
```

The release fixtures create the driver and server; run them independently so one container lifecycle cannot mask the other:

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest'
```

Inside either fixture, execute the same facade calls:

```kotlin
val alice = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val again = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "A. Example"))
check(alice.id == again.id)
check(ops.countVertices("Person") == 1L)
```

## Observe semantic differences and lifecycle

Expected: native merge preserves one identity and applies new set properties. Then inject an exception inside `transaction { createVertex(...); error("rollback") }`; the post-transaction count must stay unchanged. Run each backend's schema-manager test as well. If CRUD passes but schema fails, compare the generated DDL and server version rather than treating driver compatibility as schema compatibility. Close the operations object created by the fixture and let the fixture stop its container; an externally supplied Driver remains caller-owned.

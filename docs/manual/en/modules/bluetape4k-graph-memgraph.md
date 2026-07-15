# bluetape4k-graph-memgraph

## Choose or avoid

Memgraph uses the Neo4j Java Driver over Bolt but has its own server, Cypher subset, schema DDL, and operational model. Choose it when Memgraph is already deployed or its in-memory/streaming design fits the workload. Avoid using this adapter as proof of Neo4j parity. Source: [MemgraphGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt).

## Dependency and quick start

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-memgraph")
}
```

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = MemgraphGraphOperations(driver)
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
ops.close()
driver.close()
```

Expected: the Memgraph database returns the created neighbor. Use authentication matching the deployed server.

## Semantics and capability boundary

Transactions use the driver's Memgraph session and must be tested against the deployed Memgraph version. Merge and batch queries are adapter-specific. Schema is implemented in [MemgraphGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt); never copy Neo4j DDL assumptions into it.

Operations do not close an injected Driver. Close sessions/operations first, then the caller-owned Driver.

## Failures and operations

Diagnose network/authentication, database selection, query support, schema syntax, and transaction behavior separately. Observe pool pressure, query latency, memory, server logs, indexes, and rollback counts. If CRUD passes but schema fails, compare the generated Memgraph DDL and server version.

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
```

Expected: the Memgraph container passes CRUD and its own schema assertions. A Neo4j test cannot substitute for this command.

## Related pages and non-goals

See [Neo4j and Memgraph](../backends/neo4j-and-memgraph.md), [backend selection](../backends/selection-guide.md), and [failure and cancellation](../guides/failure-and-cancellation.md). The module does not make Memgraph a uniform Neo4j superset, provision the server, or own the Driver.

# bluetape4k-graph-memgraph

## Before you run

Memgraph uses the Neo4j Java Driver over Bolt but has its own server, Cypher subset, schema DDL, and operational model. Choose it when Memgraph is already deployed or its in-memory/streaming design fits the workload. Avoid using this adapter as proof of Neo4j parity. Source: [MemgraphGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt).


Execution mode: **release-fixture linked**. The linked test starts the exact Memgraph image, creates Driver and `ops`, seeds data, and closes operations before Driver/container; its fixture is the source of endpoint and authentication settings.

## Run

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

## Expected result

Expected: the Memgraph database returns the created neighbor. Use authentication matching the deployed server.

## Semantics and capability boundary

Transactions use the driver's Memgraph session and must be tested against the deployed Memgraph version. Merge and batch queries are adapter-specific. Schema is implemented in [MemgraphGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt); never copy Neo4j DDL assumptions into it.

Operations do not close an injected Driver. Close sessions/operations first, then the caller-owned Driver.

## Operations checklist

- Record server/image version and selected graph/database.
- Watch connection-pool pressure and query latency.
- Verify transaction rollback and schema capability separately.
- Close operations before caller-owned Driver/DataSource.

## Failure and recovery

Symptom: CRUD passes but schema or transaction assertions fail. Compare Memgraph server version and generated DDL, remove incompatible indexes/test data, and rerun the Memgraph-specific selector.

Diagnose network/authentication, database selection, query support, schema syntax, and transaction behavior separately. Observe pool pressure, query latency, memory, server logs, indexes, and rollback counts. If CRUD passes but schema fails, compare the generated Memgraph DDL and server version.

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
```

Expected: the Memgraph container passes CRUD and its own schema assertions. A Neo4j test cannot substitute for this command.

## Complete release example

The pinned [MemgraphGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [Neo4j and Memgraph](../backends/neo4j-and-memgraph.md), [backend selection](../backends/selection-guide.md), and [failure and cancellation](../guides/failure-and-cancellation.md). The module does not make Memgraph a uniform Neo4j superset, provision the server, or own the Driver.

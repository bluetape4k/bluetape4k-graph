# bluetape4k-graph-falkordb

## Before you run

FalkorDB is a Redis-shaped graph service accessed with jfalkordb 0.8.0 and an openCypher subset. Choose it when that deployed service and query subset match the system. Avoid treating it as Neo4j because query, schema, transaction, and operational behavior differ. Source: [FalkorDBGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt).


Execution mode: **release-fixture linked**. The linked test owns the FalkorDB container, jfalkordb Driver, graph name, `ops`, and cleanup. The snippet is the essential flow after those fixture values are available.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-falkordb")
}
```

```kotlin
val driver = FalkorDB.driver("localhost", 6379)
val ops = FalkorDBGraphOperations(driver, graphName = "social")
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
ops.close()
driver.close()
```

## Expected result

Expected: the graph is lazily created on the first query and traversal returns Bob.

## Semantics and unsupported boundaries

Merge and schema behavior are FalkorDB-specific; see [FalkorDBGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt). In 0.6.0 the common suspend transaction DSL is explicitly unsupported; do not replace it with client-side pseudo-atomicity. Design multi-write work as idempotent steps or choose a backend with the needed transaction boundary. Evidence: [FalkorDBGraphSuspendOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt).

The caller owns the jfalkordb Driver; operations open and close graph contexts per call.

## Operations checklist

- Record server/image version and selected graph/database.
- Watch connection-pool pressure and query latency.
- Verify transaction rollback and schema capability separately.
- Close operations before caller-owned Driver/DataSource.

## Failure and recovery

Symptom: the common transaction API reports unsupported. Do not retry or emulate atomicity; redesign as idempotent steps or select a graph implementation with the required transaction boundary.

Check readiness/network/authentication, client pool, graph name, query subset, indexes, memory, and slow queries in that order. An unsupported transaction is a capability result, not a transient server failure.

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest' --tests '*FalkorDBGraphSuspendOperationsTest'
```

Expected: CRUD/traversal passes and the transaction test records the unsupported path. If it unexpectedly appears atomic, verify the test selector and release source before relying on it.

## Complete release example

The pinned [FalkorDBGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [FalkorDB guide](../backends/falkordb.md), [backend selection](../backends/selection-guide.md), and [operations](../guides/operations.md). This module does not provision Redis/FalkorDB, make openCypher implementations interchangeable, or provide a hidden transaction fallback.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### Overview diagram

[![Overview diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-falkordb-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-falkordb-architecture-01.svg)

_Release README: [`graph/graph-falkordb/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/README.md)_

<!-- release-readme-diagrams:end -->

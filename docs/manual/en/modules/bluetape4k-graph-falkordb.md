# bluetape4k-graph-falkordb

## Choose or avoid

FalkorDB is a Redis-shaped graph service accessed with jfalkordb and an openCypher subset. Choose it when that deployed service and query subset match the system. Avoid treating it as Neo4j because query, schema, transaction, and operational behavior differ. Source: [FalkorDBGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt).

## Dependency and quick start

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

Expected: the graph is lazily created on the first query and traversal returns Bob.

## Semantics and unsupported boundaries

Merge and schema behavior are FalkorDB-specific; see [FalkorDBGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt). In 0.5.1 the common suspend transaction DSL is explicitly unsupported; do not replace it with client-side pseudo-atomicity. Design multi-write work as idempotent steps or choose a backend with the needed transaction boundary. Evidence: [FalkorDBGraphSuspendOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt).

The caller owns the jfalkordb Driver; operations open and close graph contexts per call.

## Failures and operations

Check readiness/network/authentication, client pool, graph name, query subset, indexes, memory, and slow queries in that order. An unsupported transaction is a capability result, not a transient server failure.

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest' --tests '*FalkorDBGraphSuspendOperationsTest'
```

Expected: CRUD/traversal passes and the transaction test records the unsupported path. If it unexpectedly appears atomic, verify the test selector and release source before relying on it.

## Related pages and non-goals

See [FalkorDB guide](../backends/falkordb.md), [backend selection](../backends/selection-guide.md), and [operations](../guides/operations.md). This module does not provision Redis/FalkorDB, make openCypher implementations interchangeable, or provide a hidden transaction fallback.

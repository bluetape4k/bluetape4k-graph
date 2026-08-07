# Schema, writes, and transaction boundaries

![Core abstraction map](../../assets/architecture/core-abstraction-map.png)

`VertexLabel` and `EdgeLabel` are Exposed-style declarations for reusable names and property definitions. They describe the domain; schema DDL is performed by `GraphSchemaManager`. Sources: [`VertexLabel.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/VertexLabel.kt), [`EdgeLabel.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/EdgeLabel.kt), and the [`code graph schema`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt).

```kotlin
object Person : VertexLabel("Person") { val email = string("email") }
val schema = ops.schemaManager()
schema.createIndex(Person.label, Person.email.name)
```

Schema support is a capability. Unsupported mutation must throw rather than report success; metadata lists may be empty. Verify the selected backend's manager and tests, starting with [`GraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/GraphSchemaManager.kt).

`mergeVertex`/`mergeEdge` express upsert intent, while `createVertices`/`createEdges` express batching. They do not make a multi-step workflow atomic. Use `transaction {}` or `suspendTransaction {}` only when the implementation exposes the matching capability. The block exposes vertex/edge CRUD, excludes session DDL, commits on success, and rolls back on failure. There is no auto-commit fallback: [`GraphTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

Before production, test duplicate merge keys, empty batches, mid-batch failure, rollback, cancellation, and a returned `Flow` consumed before commit. Neo4j's release tests provide concrete transaction evidence in [`Neo4jGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

## Exercise every write boundary

```kotlin
val people = ops.createVertices("Person", listOf(mapOf("email" to "a@x"), mapOf("email" to "b@x")))
val edge = ops.mergeEdge(people[0].id, people[1].id, "KNOWS", setProperties = mapOf("since" to 2024))
val path = ops.shortestPath(people[0].id, people[1].id, PathOptions(edgeLabel = "KNOWS", maxDepth = 1))
check(people.size == 2 && path?.edges?.single()?.id == edge.id)
```

The default batch contract preserves input order but may be partially applied because it calls single-item methods sequentially. A production backend override may provide all-or-fail behavior; verify its batch test instead of assuming it. Empty input returns an empty list without a backend call.

```kotlin
val before = ops.countVertices("Person")
runCatching { ops.transaction { createVertex("Person"); error("rollback") } }
check(ops.countVertices("Person") == before)
```

## Observe unsupported and cancellation paths

Schema: run `createIndex`, list it, then drop it. An unsupported manager must throw on mutation rather than silently succeed. Traversal: `maxDepth=0`/invalid limits are validated, no path returns `null`, and `allPaths` returns an empty list. Cancellation of `suspendTransaction` must roll back and rethrow cancellation; returned flows must be materialized before commit according to the backend tests.

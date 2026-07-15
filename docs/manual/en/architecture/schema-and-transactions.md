# Schema, writes, and transaction boundaries

`VertexLabel` and `EdgeLabel` are Exposed-style declarations for reusable names and property definitions. They describe the domain; schema DDL is performed by `GraphSchemaManager`. Sources: [`VertexLabel.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/VertexLabel.kt), [`EdgeLabel.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/EdgeLabel.kt), and the [`code graph schema`](../../../../examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt).

```kotlin
object Person : VertexLabel("Person") { val email = string("email") }
val schema = ops.schemaManager()
schema.createIndex(Person.label, Person.email.name)
```

Schema support is a capability. Unsupported mutation must throw rather than report success; metadata lists may be empty. Verify the selected backend's manager and tests, starting with [`GraphSchemaManager.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/GraphSchemaManager.kt).

`mergeVertex`/`mergeEdge` express upsert intent, while `createVertices`/`createEdges` express batching. They do not make a multi-step workflow atomic. Use `transaction {}` or `suspendTransaction {}` only when the implementation exposes the matching capability. The block exposes vertex/edge CRUD, excludes session DDL, commits on success, and rolls back on failure. There is no auto-commit fallback: [`GraphTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

Before production, test duplicate merge keys, empty batches, mid-batch failure, rollback, cancellation, and a returned `Flow` consumed before commit. Neo4j's release tests provide concrete transaction evidence in [`Neo4jGraphSuspendOperationsTest.kt`](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt).

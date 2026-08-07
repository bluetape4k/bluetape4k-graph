# Paired APIs and capabilities

![Core abstraction map](../../assets/architecture/core-abstraction-map.png)

`GraphOperations` combines `GraphSession`, vertex and edge repositories, and `GraphGenericRepository`; the latter combines traversal and algorithms. The coroutine counterpart mirrors this shape with suspending calls and `Flow`. See [`GraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), [`GraphSuspendOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt), and [`GraphGenericRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphGenericRepository.kt).

`GraphSession` owns graph create/drop/existence operations, but closing an operations object does not imply ownership of an injected driver or data source. Driver lifetime remains with the caller or framework container; the contract is documented in [`GraphSession.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt).

CRUD, merge/upsert, batch, traversal, schema, and transaction support are separate concerns. Batch defaults may call single-item methods sequentially and leave earlier writes after a failure; backend overrides define stronger behavior. Inspect [`GraphVertexRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexRepository.kt), [`GraphEdgeRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphEdgeRepository.kt), and [`GraphMergeOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphMergeOperationsTest.kt).

Choose the synchronous API for blocking call chains or virtual-thread isolation. Choose the suspend API when cancellation and `Flow` consumption belong to a coroutine scope. Do not mix them by wrapping arbitrary calls in `runBlocking`; use the provided backend implementation or virtual-thread adapters and test cancellation explicitly.

## Compare the repository surfaces

```kotlin
fun sync(ops: GraphOperations): List<GraphVertex> {
    check(ops.graphExists("social"))
    return ops.findVerticesByLabel("Person")
}

suspend fun async(ops: GraphSuspendOperations): List<GraphVertex> {
    check(ops.graphExists("social"))
    return ops.findVerticesByLabel("Person").toList()
}
```

Both facades include session, vertex, edge, traversal, and algorithm contracts. Merge, schema, and transaction remain capability extensions: call them only through the provided extensions, which throw `UnsupportedOperationException` when the implementation does not implement the capability.

```kotlin
val first = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val second = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice 2"))
check(first.id == second.id && ops.countVertices("Person") == 1L)
```

## Distinguish validation from capability failure

Inject an empty `matchProperties`: expect `IllegalArgumentException`. Use an implementation without `GraphMergeOperations`: expect an unsupported-capability error before any read/write fallback. These two failures distinguish invalid input from an absent backend feature.

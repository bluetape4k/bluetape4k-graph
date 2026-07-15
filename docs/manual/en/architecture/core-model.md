# Core model

`GraphElementId` is a nonblank string value class used across backends. Long and arbitrary driver IDs are normalized, but the value remains opaque to application code. See [`GraphElementId.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphElementId.kt) and its [`tests`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphElementIdTest.kt).

`GraphVertex(id, label, properties)` and `GraphEdge(id, label, startId, endId, properties)` are immutable, serializable snapshots. Property values may be null; supported runtime types still depend on the backend or format. Sources: [`GraphVertex.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt), [`GraphEdge.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphEdge.kt).

`GraphPath` contains alternating `PathStep.VertexStep` and `PathStep.EdgeStep` values for full paths. `vertices`, `edges`, `length`, and `totalWeight` are derived views; a path created only from vertices has no inferred edges. See [`GraphPath.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphPath.kt) and [`GraphPathTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphPathTest.kt).

```kotlin
val id = GraphElementId.of("person:42")
val person = GraphVertex(id, "Person", mapOf("name" to "Ada"))
```

Treat returned objects as observations, not live entities. Write changes with repository methods, keep external import IDs separate from backend IDs, and do not parse an ID to recover backend meaning.

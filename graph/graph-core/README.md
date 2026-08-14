# graph-core

Common abstraction layer for Graph Databases (Apache AGE, Neo4j, Memgraph, Apache TinkerPop). Provides backend-independent models and repository interfaces so that multiple graph database implementations can work under the same API.

> 🇰🇷 [한국어 문서](README.ko.md)

## Module Description

- **Backend-Independent Abstraction**: Common interface for various graph databases (Apache AGE, Neo4j, Memgraph, TinkerPop, etc.)
- **Coroutine-Based API**: All suspend-variant repository methods use Kotlin Coroutines
- **Dual API Pattern**: Both synchronous (`GraphOperations`) and coroutine (`GraphSuspendOperations`) interfaces
- **Schema DSL**: Declarative schema definition through `VertexLabel` and `EdgeLabel`
- **Path Tracing**: Shortest-path and all-paths results represented with the `GraphPath` model

## Architecture Overview

![Architecture Overview diagram](../../docs/images/readme-diagrams/graph-graph-core-architecture-01.png)

## Key Classes

### Model Layer

```kotlin
@JvmInline
value class GraphElementId(val value: String)

data class GraphVertex(
    val id: GraphElementId,
    val label: String,
    val properties: Map<String, Any?>,
)

data class GraphEdge(
    val id: GraphElementId,
    val label: String,
    val startId: GraphElementId,
    val endId: GraphElementId,
    val properties: Map<String, Any?>,
)

sealed class PathStep {
    data class VertexStep(val vertex: GraphVertex) : PathStep()
    data class EdgeStep(val edge: GraphEdge) : PathStep()
}

data class GraphPath(val steps: List<PathStep>)
```

### Repository Layer

```
GraphOperations = GraphSession
                + GraphVertexRepository
                + GraphEdgeRepository
                + GraphTraversalRepository

GraphSuspendOperations = GraphSuspendSession
                       + GraphSuspendVertexRepository
                       + GraphSuspendEdgeRepository
                       + GraphSuspendTraversalRepository
```

| Interface | Responsibility |
|-----------|----------------|
| `GraphSession` / `GraphSuspendSession` | Graph lifecycle and connection-facing session operations |
| `GraphVertexRepository` | Vertex CRUD (create / find / update / delete / count) |
| `GraphEdgeRepository` | Edge CRUD and relationship queries |
| `GraphTraversalRepository` | `neighbors`, `shortestPath`, `allPaths`, etc. |
| `GraphTransactionalOperations` | Optional sync transaction capability used by `ops.transaction { }` |
| `GraphSuspendTransactionalOperations` | Optional coroutine transaction capability used by `ops.suspendTransaction { }` |
| `GraphSchemaManagementOperations` | Optional sync schema/index capability used by `ops.schemaManager()` |
| `GraphSuspendSchemaManagementOperations` | Optional coroutine schema/index capability used by `suspendOps.schemaManager()` |
| `GraphMergeOperations` | Optional sync merge/upsert capability used by `ops.mergeVertex()` and `ops.mergeEdge()` |
| `GraphSuspendMergeOperations` | Optional coroutine merge/upsert capability used by suspend merge extensions |

## Traversal and Algorithm APIs

![Traversal and Algorithm APIs diagram](../../docs/images/readme-diagrams/graph-graph-core-traversal-algorithm-15.png)

`GraphTraversalRepository` answers path-oriented questions:

- `neighbors(startId, NeighborOptions)` returns nearby `GraphVertex` values.
- `shortestPath(fromId, toId, PathOptions)` returns the best bounded `GraphPath?`.
- `allPaths(fromId, toId, PathOptions)` returns all bounded simple `GraphPath` results.
- A* path search via `aStarPath(fromId, toId, PathOptions, heuristic)` returns a weighted best `GraphPath?`.

`GraphAlgorithmRepository` answers graph-level analytics questions:

- `pageRank(PageRankOptions)` returns `PageRankScore` results sorted by score descending.
- `degreeCentrality(vertexId, DegreeOptions)` returns a `DegreeResult` with in/out/total degree.
- `connectedComponents(ComponentOptions)` returns `GraphComponent` groups.
- `bfs(startId, BfsDfsOptions)` and `dfs(startId, BfsDfsOptions)` return ordered `TraversalVisit` events.
- `detectCycles(CycleOptions)` returns `GraphCycle` paths.

### Optional Native Algorithm Provider SPI

`graph-core` exposes a dependency-free provider boundary through
`GraphAlgorithmProvider`, `GraphAlgorithmProviderDescriptor`, and
`GraphAlgorithmProviderSelector`. Optional modules can advertise native
capabilities without adding a GDS/MAGE SDK to a base backend. `AUTO` selects a
provider only when its descriptor lists the requested algorithm; otherwise the
selector returns an explicit `JVM_FALLBACK` observation. `NATIVE_ONLY` fails
with `GraphAlgorithmProviderUnavailableException` instead of silently changing
the execution path.

```kotlin
val execution = GraphAlgorithmProviderSelector.select(GraphAlgorithmId.PAGE_RANK)
check(execution.path == GraphAlgorithmExecutionPath.JVM_FALLBACK)
```

Native provider modules and their driver calls are intentionally outside this
module. Backends expose the last selected path through
`GraphAlgorithmExecutionObservable` and may receive a
`GraphAlgorithmExecutionObserver` for metrics or audit logging.

### Transaction DSL

Backends that implement `GraphTransactionalOperations` can expose a sync transaction block through the
`transaction` extension. Unsupported backends fail explicitly instead of silently falling back to auto-commit.

```kotlin
import io.bluetape4k.graph.repository.transaction

val edge = ops.transaction {
    val alice = createVertex("Person", mapOf("name" to "Alice"))
    val bob = createVertex("Person", mapOf("name" to "Bob"))
    createEdge(alice.id, bob.id, "KNOWS")
}
```

Coroutine backends that implement `GraphSuspendTransactionalOperations` expose the same vertex/edge CRUD scope through
`suspendTransaction`.

```kotlin
import io.bluetape4k.graph.repository.suspendTransaction

val edge = suspendOps.suspendTransaction {
    val alice = createVertex("Person", mapOf("name" to "Alice"))
    val bob = createVertex("Person", mapOf("name" to "Bob"))
    createEdge(alice.id, bob.id, "KNOWS")
}
```

This first slice adds transaction support for Neo4j, Memgraph, AGE, and TinkerGraph sync/coroutine backends.
FalkorDB remains explicitly unsupported because its Redis `MULTI` API queues graph query results until `EXEC`, while
this repository DSL needs each created vertex ID immediately for later calls in the same block.

### Schema / Index Manager

Backends that implement `GraphSchemaManagementOperations` expose schema DDL through `schemaManager()`.
The API validates labels and property names before building backend DDL, and unsupported backends fail explicitly
instead of silently pretending that constraints were enforced.

```kotlin
import io.bluetape4k.graph.schema.schemaManager

ops.schemaManager().createIndex("Person", "email")
ops.schemaManager().createUniqueConstraint("Person", "email")
val indexes = ops.schemaManager().listIndexes()
```

The same manager is available for coroutine backends:

```kotlin
val schema = suspendOps.schemaManager()
schema.createIndex("Person", "email")
```

Support matrix:

| Backend | Indexes | Unique constraints | Notes |
|---------|---------|--------------------|-------|
| Neo4j | Create / list / drop | Create / list | Uses Neo4j `CREATE INDEX` and `CREATE CONSTRAINT` |
| Memgraph | Create / list / drop | Create / list | Uses Memgraph `SHOW INDEX INFO` and `SHOW CONSTRAINT INFO` |
| TinkerGraph | In-memory recorded no-op | Unsupported | Constraints cannot be enforced by TinkerGraph |
| AGE | Unsupported | Unsupported | PostgreSQL-side AGE indexes are not portable yet |
| FalkorDB | Create / list / drop | Unsupported | Unique constraints require raw `GRAPH.CONSTRAINT CREATE` support |

### Merge / Upsert

Backends that implement `GraphMergeOperations` expose idempotent vertex and edge upserts through extension functions.
`matchProperties` are stable identity keys and cannot be empty for vertices. `setProperties` are applied to both the
create and match branches and cannot overwrite match keys.

```kotlin
import io.bluetape4k.graph.repository.mergeEdge
import io.bluetape4k.graph.repository.mergeVertex

val alice = ops.mergeVertex(
    label = "Person",
    matchProperties = mapOf("email" to "alice@example.com"),
    setProperties = mapOf("name" to "Alice", "age" to 31),
)

val edge = ops.mergeEdge(
    fromId = alice.id,
    toId = bob.id,
    label = "KNOWS",
    setProperties = mapOf("since" to 2024),
)
```

Coroutine backends expose the same API as suspend functions. Merge keys are validated before query construction, so
unsafe labels and property names fail before reaching the backend.

Support matrix:

| Backend | Vertex merge | Edge merge | Notes |
|---------|--------------|------------|-------|
| Neo4j | Native `MERGE` | Native relationship `MERGE` | Uses `elementId()` for endpoints |
| Memgraph | Native `MERGE` | Native relationship `MERGE` | Uses integer `id()` endpoint lookup |
| FalkorDB | Native `MERGE` | Native relationship `MERGE` | Uses per-property parameters |
| AGE | Transactional match/update/create fallback | Transactional match/update/create fallback | AGE image does not support `ON CREATE SET` / `ON MATCH SET` |
| TinkerGraph | Gremlin get-or-create/update | Gremlin get-or-create/update | In-memory semantics |

### Schema DSL

Exposed Table-style declarative schema. Works across backends.

```kotlin
object PersonLabel : VertexLabel("Person") {
    val id       = string("id")
    val name     = string("name")
    val age      = integer("age")
    val email    = string("email")
    val joinedAt = localDate("joinedAt")
}

object KnowsLabel : EdgeLabel("KNOWS", PersonLabel, PersonLabel) {
    val since = localDate("since")
}
```

## Model Builder Utilities

Convenience top-level functions for constructing model objects without verbose constructors.

```kotlin
// GraphElementId
val id1 = graphElementIdOf("node-abc")          // from String
val id2 = graphElementIdOf(42L)                  // from Long → "42"
val id3 = graphElementIdOf(existingId)           // GraphElementId pass-through (no double-wrap)

// GraphVertex
val v1 = graphVertexOf(GraphElementId.of("v1"), "Person", mapOf("name" to "Alice"))
val v2 = graphVertexOf("v2", "Person")           // Any id overload
val v3 = graphVertexOf(42L, "Item", mapOf("weight" to 10.0))

// GraphPath — vararg overloads
val pathFromSteps    = graphPathOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1), PathStep.VertexStep(v2))
val pathFromVertices = graphPathOf(v1, v2, v3)   // vertex-only path
val pathFromEdges    = graphPathOf(e1, e2)        // edge-only path
val empty            = emptyGraphPath()           // GraphPath.EMPTY

// GraphCycle
val cycle = detectedPath.toCycle()               // GraphPath → GraphCycle
println("cycle length = ${cycle.length}")
```

## Usage Example

```kotlin
// Create vertex
val alice = graphOps.createVertex(
    label = "Person",
    properties = mapOf(
        "name" to "Alice",
        "age" to 30,
        "email" to "alice@example.com",
    ),
)

// Create edge
val knows = graphOps.createEdge(
    startId = alice.id,
    endId = bob.id,
    label = "KNOWS",
    properties = mapOf("since" to LocalDate.now()),
)

// Traverse
val neighbors = graphOps.neighbors(
    vertexId = alice.id,
    edgeLabel = "KNOWS",
    direction = Direction.OUTGOING,
    depth = 1,
)

// Shortest path
val path = graphOps.shortestPath(
    fromId = alice.id,
    toId = charlie.id,
    edgeLabel = "KNOWS",
    maxDepth = 5,
)
```

## Dependencies

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-core:<version>")

    // pick one backend
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-neo4j:<version>")
    // implementation("io.github.bluetape4k.graph:bluetape4k-graph-age:<version>")
    // implementation("io.github.bluetape4k.graph:bluetape4k-graph-memgraph:<version>")
    // implementation("io.github.bluetape4k.graph:bluetape4k-graph-tinkerpop:<version>")
}
```

## References

- [bluetape4k](https://github.com/bluetape4k/bluetape4k-projects) — base ecosystem
- [Apache AGE](https://age.apache.org/) — PostgreSQL graph extension
- [Neo4j](https://neo4j.com/) — native graph database
- [Memgraph](https://memgraph.com/) — in-memory graph database
- [Apache TinkerPop](https://tinkerpop.apache.org/) — graph computing framework

## Weighted Shortest Path

`graph-core` ships pure-JVM `DijkstraRunner` and `AStarRunner` used by all backends via `ShortestPathFallback`. Both algorithms read `PathOptions.weightProperty` from edge properties and delegate fetching to backend-specific lambdas.

### PathOptions

| Field | Default | Description |
|-------|---------|-------------|
| `weightProperty` | `null` | Edge property name for weight. Required for weighted traversal. |
| `edgeLabel` | `null` | Filter edges by label (`null` = all labels). |
| `missingWeightPolicy` | `Fail` | What to do when an edge lacks the weight property. |
| `direction` | `OUTGOING` | Edge direction to follow. |
| `maxVisited` | `Int.MAX_VALUE` | Abort if more vertices are visited. |

### MissingWeightPolicy

```kotlin
sealed class MissingWeightPolicy {
    object Fail    : MissingWeightPolicy()   // throws MissingWeightException (default)
    object Skip    : MissingWeightPolicy()   // treats edge as absent (returns null path)
    data class UseDefault(val weight: Double) : MissingWeightPolicy()  // substitutes value
}
```

### Usage Example

```kotlin
val ops: GraphOperations = Neo4jGraphOperations(driver)

// Dijkstra — weighted shortest path
val path = ops.shortestPath(
    fromId = alice.id,
    toId   = charlie.id,
    options = PathOptions(
        weightProperty      = "cost",
        edgeLabel           = "ROAD",
        missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
    ),
)
println("total cost: ${path?.totalWeight}")

// A* — with Euclidean heuristic
val aStarPath = ops.aStarPath(
    fromId  = alice.id,
    toId    = charlie.id,
    options = PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
) { v ->
    val vx = v.properties["x"] as? Double ?: 0.0
    val vy = v.properties["y"] as? Double ?: 0.0
    sqrt((vx - goalX) * (vx - goalX) + (vy - goalY) * (vy - goalY))
}
```

### GraphPath fields

```kotlin
data class GraphPath(
    val vertices : List<GraphVertex>,
    val edges    : List<GraphEdge>,
    val steps    : List<PathStep>,          // interleaved VertexStep / EdgeStep
    val totalWeight: Double = 0.0,          // sum of edge weights (0.0 for unweighted)
)
```

## Graph Algorithms

`graph-core` defines the `GraphAlgorithmRepository` / `GraphSuspendAlgorithmRepository` interfaces and ships JVM fallback implementations (`UnionFind`, `BfsDfsRunner`, `CycleDetector`, `PageRankCalculator`) used by backends that do not have a native query for a given algorithm.

### Algorithm Support Matrix

| Algorithm | Interface method | Options type | Result type |
|-----------|-----------------|--------------|-------------|
| Dijkstra (weighted) | `shortestPath(from, to, options)` | `PathOptions` | `GraphPath?` |
| A* (weighted) | `aStarPath(from, to, options, heuristic)` | `PathOptions` | `GraphPath?` |
| PageRank | `pageRank(options)` | `PageRankOptions` | `List<PageRankScore>` |
| Degree Centrality | `degreeCentrality(vertexId, options)` | `DegreeOptions` | `DegreeResult` |
| Connected Components | `connectedComponents(options)` | `ComponentOptions` | `List<GraphComponent>` |
| BFS | `bfs(startId, options)` | `BfsDfsOptions` | `List<TraversalVisit>` |
| DFS | `dfs(startId, options)` | `BfsDfsOptions` | `List<TraversalVisit>` |
| Cycle Detection | `detectCycles(options)` | `CycleOptions` | `List<GraphCycle>` |

### Composite Interface

```
GraphOperations = GraphSession
                + GraphVertexRepository
                + GraphEdgeRepository
                + GraphGenericRepository      // traversal + algorithm
                + GraphVirtualThreadAlgorithmRepository

GraphSuspendOperations = GraphSuspendSession
                       + GraphSuspendVertexRepository
                       + GraphSuspendEdgeRepository
                       + GraphSuspendGenericRepository
```

### Usage Example

```kotlin
val ops: GraphOperations = Neo4jGraphOperations(driver)

// PageRank — top 10 persons
val top10 = ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 10))
top10.forEach { println("${it.vertex.label}: ${it.score}") }

// Degree centrality
val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
println("in=${degree.inDegree} out=${degree.outDegree}")

// BFS
val visits = ops.bfs(alice.id, BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3))

// Cycle detection
val cycles = ops.detectCycles(CycleOptions(edgeLabel = "KNOWS", maxDepth = 5))
```

## Virtual Threads

`GraphAlgorithmRepository` can be wrapped with a Virtual Thread adapter to expose `CompletableFuture`-based async APIs for Java interop.

```kotlin
import io.bluetape4k.graph.vt.asVirtualThread

val ops: GraphOperations = TinkerGraphOperations()

// Wrap with virtual-thread executor
val vtOps = ops.asVirtualThread()

// Returns CompletableFuture<List<PageRankScore>>
val future = vtOps.pageRankAsync(PageRankOptions(topK = 5))
val scores = future.join()

// Composed pipeline
val pipeline = vtOps.pageRankAsync()
    .thenApply { list -> list.take(3) }
    .thenAccept { top -> top.forEach { println(it) } }
pipeline.join()
```

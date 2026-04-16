# Module graph-neo4j

`GraphOperations` interface implementation that uses Neo4j Java Driver 5.x + Kotlin Coroutines.
It bridges the Reactive Streams API through `kotlinx-coroutines-reactive` to provide Virtual Thread / Coroutine-friendly, non-blocking access to Neo4j.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

- **Reactive-Coroutine Bridge**: All queries are exposed as `suspend` methods via `Neo4jCoroutineSession`
- **Path Mapping**: `Neo4jRecordMapper` converts Neo4j `Path` objects to `PathStep` lists (interleaved `VertexStep` + `EdgeStep`)
- **Direction-Based Traversal**: `neighbors` queries support `OUTGOING` / `INCOMING` / `BOTH` patterns
- **elementId()**: Uses `elementId()` for stable record lookup and updates (replacement for the deprecated `id()`)

```mermaid
graph TD
    App["Application"]
    OpsIface["GraphOperations<br/>(graph-core)"]
    Impl["Neo4jGraphOperations"]
    Session["Neo4jCoroutineSession"]
    Mapper["Neo4jRecordMapper"]
    ReactiveSession["ReactiveSession<br/>(Neo4j Driver)"]
    Cypher["Cypher query engine"]
    Neo4j["Neo4j Database"]

    App --> OpsIface
    OpsIface <|.. Impl
    Impl --> Session
    Impl --> Mapper
    Session --> ReactiveSession
    ReactiveSession --> Cypher
    Cypher --> Neo4j
```

## Key Classes

| Class | Description |
|-------|-------------|
| `Neo4jGraphOperations` | Synchronous `GraphOperations` implementation over the Neo4j driver |
| `Neo4jGraphSuspendOperations` | Coroutine-based `GraphSuspendOperations` implementation |
| `Neo4jCoroutineSession` | Bridges `ReactiveSession` and Kotlin Coroutines |
| `Neo4jRecordMapper` | Converts Neo4j `Record`, `Node`, `Relationship`, and `Path` to graph-core domain types |

## Key Methods

### Vertex Management
- `createVertex(label, properties)` — Create a new node
- `findVertexById(label, id)` — Look up by `elementId()`
- `updateVertex(label, id, properties)` — Update properties
- `deleteVertex(label, id)` — Delete node
- `countVertices(label)` — Count nodes of label

### Edge Management
- `createEdge(startId, endId, label, properties)` — Create a relationship
- `findEdgeById(label, id)` — Look up by `elementId()`
- `deleteEdge(label, id)` — Delete relationship

### Graph Traversal
- `neighbors(vertexId, edgeLabel, direction, depth)` — Fetch neighboring nodes
- `shortestPath(fromId, toId, edgeLabel, maxDepth)` — Find shortest path
- `allPaths(fromId, toId, edgeLabel, maxDepth)` — Enumerate all paths

## Usage Example

### Create Driver and GraphOperations

```kotlin
import org.neo4j.driver.GraphDatabase
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations

// Driver is externally managed
val driver = GraphDatabase.driver("bolt://localhost:7687")

// Create GraphOperations
val graphOps = Neo4jGraphOperations(driver, database = "neo4j")
```

### createVertex Example

```kotlin
runTest {
    val user = graphOps.createVertex(
        label = "User",
        properties = mapOf(
            "name" to "Alice",
            "email" to "alice@example.com",
            "age" to 30,
        ),
    )
    println("Created vertex: $user")

    val found = graphOps.findVertexById("User", user.id)
    println("Found: $found")

    val count = graphOps.countVertices("User")
    println("Total users: $count")
}
```

### shortestPath Example

```kotlin
val path = graphOps.shortestPath(
    fromId = alice.id,
    toId = charlie.id,
    edgeLabel = "KNOWS",
    maxDepth = 5,
)
path?.steps?.forEach { step ->
    when (step) {
        is PathStep.VertexStep -> println("Vertex: ${step.vertex.label}")
        is PathStep.EdgeStep   -> println("Edge: ${step.edge.label}")
    }
}
```

## Testcontainers Setup

### Dependencies

```kotlin
// build.gradle.kts
testImplementation(Libs.bluetape4k_testcontainers)
testImplementation(Libs.testcontainers_neo4j)
testImplementation(Libs.kotlinx_coroutines_test)
```

### Singleton Container Pattern

```kotlin
import org.testcontainers.containers.Neo4jContainer
import kotlinx.coroutines.test.runTest

class Neo4jGraphOperationsTest {
    companion object {
        @JvmStatic
        val neo4jContainer = Neo4jContainer("neo4j:5.18")
            .withoutAuthentication()
            .apply { start() }
    }

    @Test
    fun `should create and find vertex`() = runTest {
        val driver = GraphDatabase.driver(neo4jContainer.boltUrl)
        val graphOps = Neo4jGraphOperations(driver)
        try {
            val vertex = graphOps.createVertex("User", mapOf("name" to "Test"))
            assertEquals("User", vertex.label)
        } finally {
            driver.close()
        }
    }
}
```

## AGE vs Neo4j Comparison

| Item | Apache AGE | Neo4j |
|------|-----------|-------|
| Base DB | PostgreSQL (extension) | Native graph DB |
| Query Language | Cypher (SQL-wrapped) | Native Cypher |
| Driver | JDBC / PostgreSQL driver | Neo4j Java Driver (Reactive API) |
| Performance | Relational-optimized → slower on graph queries | Graph-optimized (still the fastest) |
| Scalability | Inherits PostgreSQL (horizontal scale is hard) | Cluster / Federation support |
| Virtual Thread / Coroutine | Plain JDBC → no Loom support | ReactiveSession → non-blocking |
| Use Case | Adding graph to existing PostgreSQL | Dedicated graph systems (social, recommendation, security) |

## Notes

### Driver Ownership
```kotlin
// Driver creation is caller-managed
val driver = GraphDatabase.driver("bolt://localhost:7687")
val graphOps = Neo4jGraphOperations(driver)

// close() on graphOps is a no-op for the driver
graphOps.close()   // no-op
driver.close()     // caller must close explicitly
```

### elementId() Usage
Neo4j 5.x replaces the deprecated `id()` with `elementId()`. All queries in this module use `elementId()` for stable record lookup.

### Cypher Parameterization
All queries use Neo4j driver parameter binding. Never concatenate user-supplied strings into Cypher.

### Depth Limits
`shortestPath` and `allPaths` enforce a `maxDepth` to prevent runaway traversals.

## Performance Tips

### Index Usage
```cypher
CREATE INDEX person_name IF NOT EXISTS FOR (p:Person) ON (p.name);
```

### Batch Operations
For bulk inserts, prefer `UNWIND $rows AS row CREATE (:Person {...row})` over a per-row loop.

### Query Profiling
```cypher
PROFILE MATCH p = shortestPath((a)-[:KNOWS*..10]-(b))
WHERE elementId(a) = $fromId AND elementId(b) = $toId
RETURN p
```

## Graph Algorithms

### Algorithm Support Matrix

| Algorithm | Implementation | Notes |
|-----------|---------------|-------|
| `degreeCentrality` | Cypher native (`OPTIONAL MATCH ... count`) | |
| `bfs` / `dfs` | JVM fallback (`BfsDfsRunner`) | |
| `detectCycles` | Cypher native (variable-length path) | |
| `connectedComponents` | JVM fallback (`UnionFind`) | |
| `pageRank` | JVM fallback (`PageRankCalculator`) | GDS optional module planned for Phase 7 |

### Usage Example

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = Neo4jGraphOperations(driver)

// Degree centrality (Cypher native)
val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
println("in=${degree.inDegree} out=${degree.outDegree} total=${degree.totalDegree}")

// Cycle detection (Cypher native)
val cycles = ops.detectCycles(CycleOptions(edgeLabel = "KNOWS", maxDepth = 5))
println("Found ${cycles.size} cycles")

// PageRank top 10 (JVM fallback)
val top10 = ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 10))
top10.forEach { println("${it.vertex.properties["name"]}: ${it.score}") }

// Coroutine variant
val suspendOps = Neo4jGraphSuspendOperations(driver)
val scores: Flow<PageRankScore> = suspendOps.pageRankFlow(PageRankOptions(topK = 10))
scores.collect { println(it) }
```

## License

Apache License 2.0

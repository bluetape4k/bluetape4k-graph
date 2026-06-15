# Module graph-neo4j

`GraphOperations` interface implementation that uses Neo4j Java Driver 5.x + Kotlin Coroutines.
It bridges the Reactive Streams API through `kotlinx-coroutines-reactive` to provide Virtual Thread / Coroutine-friendly, non-blocking access to Neo4j.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

- **Reactive-Coroutine Bridge**: All queries are exposed as `suspend` methods via `Neo4jCoroutineSession`
- **Path Mapping**: `Neo4jRecordMapper` converts Neo4j `Path` objects to `PathStep` lists (interleaved `VertexStep` + `EdgeStep`)
- **Direction-Based Traversal**: `neighbors` queries support `OUTGOING` / `INCOMING` / `BOTH` patterns
- **elementId()**: Uses `elementId()` for stable record lookup and updates (replacement for the deprecated `id()`)

![Overview diagram](../../docs/images/readme-diagrams/graph-graph-neo4j-architecture-01.png)

## Key Classes

| Class | Description |
|-------|-------------|
| `Neo4jGraphOperations` | Synchronous `GraphOperations` implementation over the Neo4j driver |
| `Neo4jGraphSuspendOperations` | Coroutine-based `GraphSuspendOperations` implementation |
| `CachingNeo4jGraphOperations` | `ConcurrentHashMap`-backed caching decorator over `Neo4jGraphOperations` |
| `Neo4jGraphSchemaManager` | Schema/index manager for Neo4j indexes and unique constraints |
| `Neo4jCoroutineSession` | Bridges `ReactiveSession` and Kotlin Coroutines |
| `Neo4jRecordMapper` | Converts Neo4j `Record`, `Node`, `Relationship`, and `Path` to graph-core domain types |

## Class Model

### Neo4jGraphOperations Implementation

![Neo4jGraphOperations diagram](../../docs/images/readme-diagrams/graph-graph-neo4j-class-03.png)

### Neo4jCoroutineSession Bridge

![Neo4jCoroutineSession diagram](../../docs/images/readme-diagrams/graph-graph-neo4j-class-04.png)

### Neo4jRecordMapper Conversion Methods

![Neo4jRecordMapper diagram](../../docs/images/readme-diagrams/graph-graph-neo4j-class-05.png)

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

### Schema / Index Management
- `ops.schemaManager().createIndex(label, property)` — Create a node property index
- `ops.schemaManager().createUniqueConstraint(label, property)` — Create a node property uniqueness constraint
- `ops.schemaManager().listIndexes()` / `listConstraints()` — Inspect schema metadata
- `ops.schemaManager().dropIndex(label, property)` — Drop the generated property index

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

### Merge / Upsert and Transaction DSL

Neo4j supports `GraphMergeOperations` with native Cypher `MERGE` and repository-style `Transaction DSL` backed by
driver transactions. Relationship merge uses Neo4j 5.x `elementId()` for endpoint lookup.

```kotlin
import io.bluetape4k.graph.repository.mergeVertex
import io.bluetape4k.graph.repository.transaction

val alice = graphOps.mergeVertex(
    label = "Person",
    matchProperties = mapOf("email" to "alice@example.com"),
    setProperties = mapOf("name" to "Alice"),
)

val edge = graphOps.transaction {
    val bob = createVertex("Person", mapOf("email" to "bob@example.com"))
    createEdge(alice.id, bob.id, "KNOWS")
}
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

### Shared Launcher Pattern

```kotlin
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import kotlinx.coroutines.test.runTest
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

class Neo4jGraphOperationsTest {
    companion object {
        private val server = Neo4jServer.Launcher.neo4j
    }

    @Test
    fun `should create and find vertex`() = runTest {
        val driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
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

## Caching Decorator

`CachingNeo4jGraphOperations` wraps a `Neo4jGraphOperations` instance and memoizes all read results using `ConcurrentHashMap` (~5 ns lookup). It is designed for read-heavy workloads such as benchmarks or repeated graph traversals.

### Cache Behaviour

| Operation | Effect |
|-----------|--------|
| `findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`, `allPaths`, `findEdgesByLabel` | Results cached on first call; subsequent calls return the cached value without hitting the DB |
| `createVertex`, `createEdge` | Write-result memoization: same arguments return the same object. Read caches are invalidated; write caches are preserved |
| `updateVertex`, `deleteVertex`, `deleteEdge` | All caches (read + write) invalidated |

> **Production note**: write-result memoization means repeated `createVertex` calls with the same arguments do not create additional DB records. Use `Neo4jGraphOperations` directly when transactional insert semantics are required.

### Usage Example

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val baseOps = Neo4jGraphOperations(driver)

// Wrap with caching decorator
val ops = CachingNeo4jGraphOperations(baseOps)

// First call: DB query
val alice = ops.findVertexById("Person", aliceId)

// Second call: cache hit (~5 ns), no DB round-trip
val aliceCached = ops.findVertexById("Person", aliceId)

// Any write invalidates all read caches automatically
ops.deleteVertex("Person", aliceId)
val afterDelete = ops.findVertexById("Person", aliceId)  // null (cache miss → DB)
```

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

MIT License

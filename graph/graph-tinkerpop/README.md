# graph-tinkerpop

`GraphOperations` / `GraphSuspendOperations` implementation based on Apache TinkerPop Gremlin.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

Implements the `graph-core` interfaces using TinkerGraph (an in-memory JVM graph DB).
It runs standalone without an external server, making it well suited for testing and prototyping.

## Key Classes

| Class | Description |
|-------|-------------|
| `TinkerGraphOperations` | Synchronous (blocking) implementation |
| `TinkerGraphSuspendOperations` | Coroutine (suspend + Flow) implementation |
| `GremlinRecordMapper` | Converts TinkerPop Vertex/Edge/Path into GraphVertex/GraphEdge/GraphPath |

## Dependencies

```kotlin
dependencies {
    api(project(":graph-core"))
    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)
}
```

## Usage Example

```kotlin
val ops = TinkerGraphOperations()

// Create vertices
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))

// Create edge
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024L))

// Traverse neighbors
val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))

ops.close()
```

## Graph Algorithms

TinkerPop uses native Gremlin traversals for all 6 algorithms — no JVM fallback needed.

### Algorithm Support Matrix

| Algorithm | Implementation | Notes |
|-----------|---------------|-------|
| `degreeCentrality` | Gremlin native (`g.V().bothE().count()`) | |
| `bfs` | Gremlin native (`repeat().breadthFirst()`) | |
| `dfs` | Gremlin native (`repeat().depthFirst()`) | |
| `detectCycles` | Gremlin native (cycle-path detection) | |
| `connectedComponents` | Gremlin native (`connectedComponent()` step) | |
| `pageRank` | Gremlin native (`pageRank()` step) | |

### Usage Example

```kotlin
val ops = TinkerGraphOperations()

// All algorithms run natively on TinkerGraph (no Docker required)
val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
println("in=${degree.inDegree} out=${degree.outDegree}")

val visits = ops.bfs(alice.id, BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3))
println("BFS visited ${visits.size} nodes")

val components = ops.connectedComponents(ComponentOptions(edgeLabel = "KNOWS"))
println("Found ${components.size} connected components")

val top10 = ops.pageRank(PageRankOptions(topK = 10))
top10.forEach { println("${it.vertex.label}: ${it.score}") }

// Virtual Thread usage
val vtOps = ops.asVirtualThread()
val future = vtOps.pageRankAsync()
val scores = future.join()
```

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

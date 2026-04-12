# graph-memgraph

`GraphOperations` / `GraphSuspendOperations` implementation for the Memgraph graph database.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

[Memgraph](https://memgraph.com/) is an in-memory graph database that is fully compatible with the Neo4j Bolt protocol and openCypher.
It can be connected to with `neo4j-java-driver` as-is.

## Key Classes

| Class | Description |
|-------|-------------|
| `MemgraphGraphOperations` | Synchronous (blocking) graph operations |
| `MemgraphGraphSuspendOperations` | Coroutine (suspend/Flow) graph operations |

## Usage

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())

// Synchronous
val ops = MemgraphGraphOperations(driver)
val vertex = ops.createVertex("Person", mapOf("name" to "Alice"))

// Coroutine
val suspendOps = MemgraphGraphSuspendOperations(driver)
val vertex = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
```

## Differences from Neo4j

| Item | Neo4j | Memgraph |
|------|-------|----------|
| Default database parameter | `"neo4j"` | `"memgraph"` |
| `elementId()` support | Yes (5.x) | Yes (2.x+) |
| `shortestPath` | Yes | Yes |
| Authentication | Basic auth | None by default (`AuthTokens.none()`) |

## Testing

Testcontainers automatically launches the `memgraph/memgraph:latest` image.

```bash
./gradlew :graph-memgraph:test
```

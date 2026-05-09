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
| `CachingMemgraphGraphOperations` | `ConcurrentHashMap`-backed caching decorator over `MemgraphGraphOperations` |
| `MemgraphGraphSchemaManager` | Schema/index manager for Memgraph indexes and unique constraints |

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

## Schema / Index Management

```kotlin
import io.bluetape4k.graph.schema.schemaManager

val schema = ops.schemaManager()
schema.createIndex("Person", "email")
schema.createUniqueConstraint("Person", "email")

val indexes = schema.listIndexes()
val constraints = schema.listConstraints()

schema.dropIndex("Person", "email")
```

Memgraph uses `CREATE INDEX ON :Label(property)`, `SHOW INDEX INFO`,
`CREATE CONSTRAINT ON (n:Label) ASSERT n.property IS UNIQUE`, and `SHOW CONSTRAINT INFO`.

## Differences from Neo4j

| Item | Neo4j | Memgraph |
|------|-------|----------|
| Default database parameter | `"neo4j"` | `"memgraph"` |
| `elementId()` support | Yes (5.x) | Yes (2.x+) |
| `shortestPath` | Yes | Yes |
| Authentication | Basic auth | None by default (`AuthTokens.none()`) |

## Graph Algorithms

Memgraph shares the same Cypher-based algorithm implementations as `graph-neo4j` (both use the Neo4j Bolt protocol).

### Algorithm Support Matrix

| Algorithm | Implementation | Notes |
|-----------|---------------|-------|
| `degreeCentrality` | Cypher native (`OPTIONAL MATCH ... count`) | |
| `bfs` / `dfs` | JVM fallback (`BfsDfsRunner`) | |
| `detectCycles` | Cypher native (variable-length path) | |
| `connectedComponents` | JVM fallback (`UnionFind`) | |
| `pageRank` | JVM fallback (`PageRankCalculator`) | Memgraph MAGE optional module planned |

### Usage Example

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = MemgraphGraphOperations(driver)

val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
val cycles = ops.detectCycles(CycleOptions(edgeLabel = "KNOWS", maxDepth = 5))
val top10  = ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 10))
```

## Caching Decorator

`CachingMemgraphGraphOperations` wraps a `MemgraphGraphOperations` instance and memoizes all read results using `ConcurrentHashMap` (~5 ns lookup). It is designed for read-heavy workloads such as benchmarks or repeated graph traversals.

### Cache Behaviour

| Operation | Effect |
|-----------|--------|
| `findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`, `allPaths`, `findEdgesByLabel` | Results cached on first call; subsequent calls return the cached value without hitting the DB |
| `createVertex`, `createEdge` | Write-result memoization: same arguments return the same object. Read caches are invalidated; write caches are preserved |
| `updateVertex`, `deleteVertex`, `deleteEdge` | All caches (read + write) invalidated |

> **Production note**: write-result memoization means repeated `createVertex` calls with the same arguments do not create additional DB records. Use `MemgraphGraphOperations` directly when transactional insert semantics are required.

### Usage Example

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val baseOps = MemgraphGraphOperations(driver)

// Wrap with caching decorator
val ops = CachingMemgraphGraphOperations(baseOps)

// First call: DB query
val alice = ops.findVertexById("Person", aliceId)

// Second call: cache hit (~5 ns), no DB round-trip
val aliceCached = ops.findVertexById("Person", aliceId)

// Any write invalidates all read caches automatically
ops.deleteVertex("Person", aliceId)
val afterDelete = ops.findVertexById("Person", aliceId)  // null (cache miss → DB)
```

## Testing

Testcontainers automatically launches the `memgraph/memgraph:latest` image.

```bash
./gradlew :graph-memgraph:test
```

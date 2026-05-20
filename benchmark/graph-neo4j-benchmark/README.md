# graph-neo4j-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH benchmarks for the Neo4j backend in `bluetape4k-graph`.

## Architecture

![graph-neo4j-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-neo4j-benchmark-architecture-01.png)

## What It Measures

`graph-neo4j-benchmark` measures `Neo4jGraphOperations` against a containerized Neo4j server.

- Vertex read/write operations: create vertex, find by label, find by id, and neighbor lookup.
- Edge write operations: create edge and compare loop inserts with batch inserts.
- Path traversal: shortest path and all paths.
- Backend lifecycle: Testcontainers starts Neo4j, the Neo4j Java Driver opens sessions, and the benchmark state seeds a small graph.

## Source Evidence

- `build.gradle.kts` applies `kotlinx.benchmark` and `kotlin("plugin.allopen")`, opens JMH `@State`, and uses JSON reports.
- The module depends on `graph-core`, `graph-neo4j`, Neo4j Java Driver, Bolt runtime modules, and Neo4j Testcontainers.
- `Neo4jBenchmarkState` starts the Neo4j container, creates `Neo4jGraphOperations`, and seeds four `Person` vertices plus four edges.
- `Neo4jVertexBenchmark` covers read/write, neighbor, and 10k loop-vs-batch insert paths.
- `Neo4jTraversalBenchmark` measures `shortestPath` and `allPaths`.
- `BenchmarkSingleThreadedCachingNeo4jGraphOperations` isolates benchmark measurement from repeated lookup overhead inside a single-threaded JMH state.

## Running

```bash
./gradlew :graph-neo4j-benchmark:benchmark
```

The benchmark starts a local containerized Neo4j instance. Keep this module out of parallel Testcontainers runs.

## Notes

- The fixture graph mirrors the AGE benchmark shape so backend operation results are easier to compare.
- Batch insert benchmarks create 10k vertices or edges to compare loop writes with Neo4j batch APIs.

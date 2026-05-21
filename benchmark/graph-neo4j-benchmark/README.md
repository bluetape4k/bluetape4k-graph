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

## Latest Cross-Backend Result

The shared `graph-benchmark` Testcontainers run also measures Neo4j with the same `GraphOperations` contract used by other backends.
Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, `small` dataset, May 21, 2026.
All values are `ms/op`; lower is better.

| Operation | Neo4j | Fastest backend in the same run |
|---|---:|---|
| `batchInsertCycle` | 6.217 | Memgraph, 1.969 |
| `countPersons` | 0.809 | TinkerGraph, 0.032 |
| `oneHopNeighbors` | 0.811 | TinkerGraph, 0.003 |
| `shortestPath` | 0.806 | TinkerGraph, 0.018 |

![Graph DB Testcontainers benchmark](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)

See the full backend matrix in `benchmark/graph-benchmark/README.md`.

## Notes

- The fixture graph mirrors the AGE benchmark shape so backend operation results are easier to compare.
- Batch insert benchmarks create 10k vertices or edges to compare loop writes with Neo4j batch APIs.

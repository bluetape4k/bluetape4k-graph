# graph-age-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH benchmarks for the Apache AGE backend in `bluetape4k-graph`.

## Architecture

![graph-age-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-age-benchmark-architecture-01.png)

## What It Measures

`graph-age-benchmark` measures `AgeGraphOperations` against a PostgreSQL + Apache AGE database started by the benchmark state.

- Vertex read/write operations: create vertex, find by label, find by id, and neighbor lookup.
- Edge write operations: create edge and compare loop inserts with batch inserts.
- Path traversal: shortest path and all paths.
- Backend lifecycle: Testcontainers starts PostgreSQL AGE, HikariCP provides JDBC pooling, and Exposed binds the `Database`.

## Source Evidence

- `build.gradle.kts` applies `kotlinx.benchmark` and `kotlin("plugin.allopen")`, opens JMH `@State`, and uses JSON reports.
- `AgeBenchmarkState` starts `PostgreSQLAgeServer.Launcher.postgresqlAge`, loads AGE through the JDBC init SQL, creates `bench_graph`, and seeds four `Person` vertices plus four edges.
- `AgeVertexBenchmark` covers read/write, neighbor, and 10k loop-vs-batch insert paths.
- `AgeTraversalBenchmark` measures `shortestPath` and `allPaths` with `PathOptions`.
- `BenchmarkSingleThreadedCachingAgeGraphOperations` isolates benchmark measurement from repeated lookup overhead inside a single-threaded JMH state.

## Running

```bash
./gradlew :graph-age-benchmark:benchmark
```

The benchmark starts a local containerized PostgreSQL AGE instance. Keep this module out of parallel Testcontainers runs.

## Notes

- The fixture graph is intentionally small for operation-level latency comparisons.
- Batch insert benchmarks create 10k vertices or edges to compare loop writes with backend batch APIs.

# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH benchmarks for backend-independent graph operations using the in-memory TinkerGraph implementation.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## What It Measures

`graph-benchmark` compares synchronous graph APIs with virtual-thread adapters over `TinkerGraphOperations`.

- Algorithm operations: PageRank, BFS, and DFS through sync and virtual-thread paths.
- Traversal operations: neighbors, shortest path, and all paths.
- Vertex operations: find by label, find by id, and neighbor lookup.
- Batch insert operations: 10k vertex and edge loop inserts versus batch inserts.

## Source Evidence

- `build.gradle.kts` applies `kotlinx.benchmark`, opens JMH `@State`, and depends on `graph-core`, `graph-tinkerpop`, coroutines, and `bluetape4k-virtualthread-api`.
- `GraphBenchmarkState` builds the shared in-memory graph with `TinkerGraphOperations`.
- `AlgorithmBenchmark` compares sync algorithm calls with `VirtualThreadAlgorithmAdapter`.
- `TraversalBenchmark`, `ShortestPathBenchmark`, `NeighborsBenchmark`, and `VertexOperationsBenchmark` compare sync and virtual-thread operation paths.
- `BatchInsertBenchmark` and the smoke test support validate 10k loop-vs-batch vertex and edge insertion scenarios.

## Running

```bash
./gradlew :graph-benchmark:benchmark
```

The module uses an in-memory TinkerGraph backend, so it does not require external graph database containers.

## Notes

- This module is the fastest benchmark lane for checking core API overhead.
- Results are useful for comparing sync calls against virtual-thread wrappers without network or database startup cost.

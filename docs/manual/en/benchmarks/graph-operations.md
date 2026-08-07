# Graph operations benchmarks

## Workloads and source

`graph-benchmark` covers backend CRUD/traversal, domain graphs, sustained writes, and sync/virtual-thread/coroutine API shapes. Read the pinned [Gradle configuration](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/build.gradle.kts), [domain benchmark](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDomainWorkloadBenchmark.kt), and [state](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphBenchmarkState.kt).

Prerequisites are JDK 21 and Docker for Neo4j/Memgraph/AGE/FalkorDB lanes. Do not run other Testcontainers workloads concurrently.

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark --rerun-tasks --console=plain
```

This named configuration selects TinkerGraph, Neo4j, and Memgraph domain cases, uses 2 warmups, 4 measurement iterations, 2 seconds per iteration, and JSON output. JMH normally uses one fork unless overridden by the harness. Expected output contains `avgt` scores in `ms/op`; lower is better. Compare social, IAM, fraud, or code rows only when backend, size, parameters, and run metadata match.

The representative committed source is [2026-05-21 domain workload JSON](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json). It is a local Testcontainers run, not a universal backend ranking. In-memory TinkerGraph and server databases have different persistence and transport boundaries.

## Variance, cleanup, and diagnosis

Repeat the command at least three times on an idle machine, retain raw JSON, and inspect score error/intervals. Warmup reduces JIT effects; forks isolate JVM state; neither removes container, GC, filesystem, or thermal variance. Benchmark state teardown closes graph operations, drivers/pools, and containers; after interruption, verify no benchmark container remains before rerunning.

A missing JSON report means the benchmark task or teardown failed. A timeout should be preserved as failure evidence, not converted into a numeric score. This benchmark does not prove cluster failover, durability, production indexes, authorization, or cost. See [overview](./overview.md) and [benchmark-based selection](../guides/benchmark-based-selection.md).

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### graph-benchmark Architecture diagram

[![graph-benchmark Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.svg)

_Release README: [`benchmark/graph-benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/README.md)_

<!-- release-readme-diagrams:end -->

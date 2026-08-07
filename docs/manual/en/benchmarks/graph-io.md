# Graph I/O benchmarks

## Workload and harness

`graph-io-benchmark` measures CSV, Jackson 2/3 NDJSON, GraphML, and OkIO export/import/round trips over in-memory TinkerGraph and temporary files. The pinned [build configuration](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/build.gradle.kts) defines 3 warmups and 5 three-second iterations for `main`; [benchmark state](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/io/BulkGraphIoBenchmarkState.kt) owns temporary resources.

Prerequisites: JDK 21, release commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and sufficient temporary disk space. Docker is not required.

```bash
./gradlew :graph-io-benchmark:smokeBenchmark --rerun-tasks --console=plain
./gradlew :graph-io-benchmark:benchmark --rerun-tasks --console=plain
```

The smoke configuration uses `sizeName=smoke`, 1 warmup, 1 measurement of 200 ms, and representative CSV/Jackson3-OkIO/GraphML-OkIO round trips. It validates wiring only. The full run writes JMH reports below `benchmark/graph-io-benchmark/build/reports/benchmarks/`. Average `ms/op` is lower-is-better; compare methods only at the same size and execution model.

The committed [2026-04-18 result](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/2026-04-18-graph-io-bulk-results.md) includes numeric small-dataset rows and run conditions. It does not justify comparing a suspend outlier with another machine or claiming a serializer wins for remote databases.

## Variance, cleanup, and diagnosis

Run three full trials, retain JSON, and compare intervals. Warmups and forks address JVM state but not filesystem cache, antivirus, compression, or disk contention. Trial teardown removes temporary directories; after interruption, inspect the module build directory and OS temporary space before rerunning.

If import counts differ, treat it as correctness failure before reading latency. If JSON is absent, inspect the first failing benchmark and teardown log. This workload does not prove remote-driver throughput, encrypted production storage, huge graphs, memory ceilings, or crash recovery. Read [graph-io execution model](../graph-io/execution-model.md) and [overview](./overview.md).

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### graph-io-benchmark Architecture diagram

[![graph-io-benchmark Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-io-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-io-benchmark-architecture-01.svg)

_Release README: [`benchmark/graph-io-benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/README.md)_

<!-- release-readme-diagrams:end -->

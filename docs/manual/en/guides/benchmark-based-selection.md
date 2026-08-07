# Benchmark-based selection

Benchmarks answer a bounded workload question; they do not rank databases universally. Graph 0.6.0 contains four evidence modules: common graph operations, graph-io, AGE, and Neo4j. Start with [`benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/README.md), then inspect each module's workload and setup.

Before comparing results, pin JVM, CPU/memory, OS/container, server image/configuration, dataset shape, warmup/measurement counts, concurrency, driver pool, transaction size, indexes, and graph state reset. Compare the same semantic operation and verify result correctness.

Use [`graph-benchmark`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/README.md) for implementation-level common workloads, [`graph-io-benchmark`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/README.md) for codec/transfer choices, and backend modules for AGE/Neo4j evidence. Do not compare numbers collected under different environments as if they were one table.

Select on required semantics and operations first. Benchmark the surviving candidates with production-shaped data, then inspect latency distribution, throughput, allocation, server CPU/memory, query plans, retries, and failures. A faster mean with missing transaction or schema semantics is not a valid replacement.

## Run a reproducible measurement

```bash
./gradlew :graph-benchmark:mainGraphDbSmallBenchmark
./gradlew :graph-io-benchmark:smokeBenchmark
```

Record the resolved benchmark list and environment before accepting output. Expected evidence includes warmup/measurement configuration, sample count, score/error units, allocation when enabled, and server-side metrics for remote backends. The graph-io smoke report is wiring evidence, not performance evidence. Inject a missing index or reduce pool size and observe both latency distribution and error/retry changes. Diagnose semantic mismatch or failed result validation before comparing speed.

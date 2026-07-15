# Benchmark-based selection

Benchmarks answer a bounded workload question; they do not rank databases universally. Graph 0.5.1 contains four evidence modules: common graph operations, graph-io, AGE, and Neo4j. Start with [`benchmark/README.md`](../../../../benchmark/README.md), then inspect each module's workload and setup.

Before comparing results, pin JVM, CPU/memory, OS/container, server image/configuration, dataset shape, warmup/measurement counts, concurrency, driver pool, transaction size, indexes, and graph state reset. Compare the same semantic operation and verify result correctness.

Use [`graph-benchmark`](../../../../benchmark/graph-benchmark/README.md) for implementation-level common workloads, [`graph-io-benchmark`](../../../../benchmark/graph-io-benchmark/README.md) for codec/transfer choices, and backend modules for AGE/Neo4j evidence. Do not compare numbers collected under different environments as if they were one table.

Select on required semantics and operations first. Benchmark the surviving candidates with production-shaped data, then inspect latency distribution, throughput, allocation, server CPU/memory, query plans, retries, and failures. A faster mean with missing transaction or schema semantics is not a valid replacement.

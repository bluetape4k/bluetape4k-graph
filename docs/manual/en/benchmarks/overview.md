# Benchmark inventory and decision procedure

## What exists in 0.6.0

The release contains four benchmark projects:

| Project | Workload | Harness and environment | Metric |
|---|---|---|---|
| `graph-benchmark` | backend CRUD, traversal, domain, ingestion, API-model workloads | kotlinx-benchmark/JMH; TinkerGraph plus serial Testcontainers backends | average time: lower is better; selected throughput: higher is better |
| `graph-io-benchmark` | CSV, NDJSON, GraphML, OkIO import/export/round trip | JMH, temporary files, in-memory TinkerGraph | `ms/op`, lower is better |
| `graph-age-benchmark` | AGE vertex, batch, neighbor, shortest/all paths | JMH, PostgreSQL AGE Testcontainer, HikariCP | `ms/op`, lower is better |
| `graph-neo4j-benchmark` | Neo4j vertex, batch, neighbor, shortest/all paths | JMH, Neo4j Testcontainer and Java driver | `ms/op`, lower is better |

Prerequisites: JDK 21, Docker for container-backed projects, release commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and an otherwise idle machine. Run container benchmarks serially.

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark
./gradlew :graph-io-benchmark:smokeBenchmark
./gradlew :graph-age-benchmark:benchmark
./gradlew :graph-neo4j-benchmark:benchmark
```

Expected observation: each task emits JMH iteration lines and a JSON report below its module's `build/reports/benchmarks/` directory. The smoke task proves wiring, not performance.

## Decision procedure

1. Choose the workload that matches query shape, data size, and execution API.
2. Compare rows only inside the same committed run: identical fixture, parameters, machine, fork, warmup, iteration, and unit.
3. Prefer confidence intervals and repeated runs over a single score.
4. Re-run with production-shaped data before choosing a backend.
5. Include operational constraints that JMH does not measure.

Committed evidence includes [backend results](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/2026-05-21-graph-db-testcontainers-results.md), [graph-io results](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/2026-04-18-graph-io-bulk-results.md), and [API-model JSON](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/2026-05-21-api-model-jmh.json). These results do not prove horizontal scale, cloud latency, failover, durability, total cost, or that one backend wins under different run conditions.

Read [graph operations](./graph-operations.md), [graph I/O](./graph-io.md), and [AGE and Neo4j](./age-and-neo4j.md). Benchmark projects are release fixtures, not published libraries. Consumer applications select `bluetape4k-dependencies:<ecosystem-version>` and add required graph modules without individual versions.

# bluetape4k-graph Benchmark Decision Guide

[English](README.md) | [한국어](README.ko.md)

This directory contains benchmark modules for choosing a graph backend, API model, and graph-io format. The guidance below summarizes the current measurements and turns them into service-oriented recommendations.

Use this guide as a starting point, not as a procurement-grade ranking. The backend matrix now includes `small`, `medium`, and sustained write ingestion local Testcontainers runs; production choices still need a workload-shaped benchmark with your data model, query mix, and deployment constraints.

## Evidence Base

| Area | Source | Current coverage | Main direction |
|---|---|---|---|
| Graph DB backend | `graph-benchmark` / `GraphDbComparisonBenchmark` | TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB on `small` and `medium` datasets | Lower `ms/op` is better |
| Domain graph workloads | `graph-benchmark` / `GraphDomainWorkloadBenchmark` | Social, IAM, fraud, and code graph workloads on TinkerGraph, Neo4j, Memgraph | Lower `ms/op` is better |
| Sustained graph writes | `graph-benchmark` / `GraphWriteIngestionBenchmark` | Vertex-only, edge-only, mixed, and repeated mixed write batches at 100, 1,000, and selective 10,000 rows | Lower `ms/op` is better |
| API model | `graph-benchmark` / `ApiModelBenchmark` | Sync, Virtual Thread, Coroutine Flow, plus production concurrency at 10, 100, and 1,000 units on TinkerGraph | PageRank `ops/s` higher is better; latency `us/op` lower is better |
| graph-io format | `graph-io-benchmark` and `graph-benchmark` | CSV, Jackson2/3 NDJSON, GraphML, Okio variants | Lower `ms/op` is better |

Latest raw artifacts:

- `docs/benchmark/graph-db-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-db-small-gradle-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-write-ingestion-10k-testcontainers-2026-05-21.json`
- `docs/benchmark/api-model-production-gradle-2026-05-21.json`
- `docs/benchmark/2026-05-21-api-model-jmh.json`
- `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

## Medium Backend Result

![Graph DB medium Testcontainers benchmark](../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, three warmup iterations, five three-second measurement iterations, `medium` dataset, May 21, 2026. All values are `ms/op`; lower is better.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 44.967 | 15.690 | **11.364** | 309.090 | 1929.180 |
| `countPersons` | 0.308 | 0.528 | 1.341 | 2.176 | **0.197** |
| `oneHopNeighbors` | **0.003** | 0.665 | 0.308 | 10.175 | 1.046 |
| `shortestPath` | **0.019** | 0.700 | 0.386 | 12.420 | 0.512 |

Medium-scale interpretation: Memgraph is the best persistent backend for write-heavy inserts and remains competitive for traversal/path queries. Neo4j is slower than Memgraph in this local benchmark but remains the default low-risk production recommendation because of operational maturity. FalkorDB read paths are competitive in `countPersons` and `shortestPath`, but medium batch insert is too slow for write-heavy workloads. AGE remains a PostgreSQL-consolidation choice, not a raw-latency choice.

## Sustained Write Ingestion Result

![Graph write ingestion Testcontainers benchmark](../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, real Testcontainers backends, GC profiler enabled, May 21, 2026. All values are `ms/op`; lower is better. `Repeated mixed batches` runs five mixed vertex+edge batches in one benchmark operation.

| Scenario | Batch | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|---:|
| Vertex-only batch insert | 100 | 2.606 | 2.740 | **0.965** | 1.347 | 1.467 |
| Vertex-only batch insert | 1,000 | 11.902 | 8.499 | **5.429** | 9.100 | 6.210 |
| Edge-only batch insert | 100 | 3.298 | 3.788 | **1.190** | 9.436 | 32.347 |
| Edge-only batch insert | 1,000 | 18.971 | 13.762 | **7.572** | 260.430 | 3026.397 |
| Mixed vertex+edge insert | 100 | 7.102 | 7.352 | **2.199** | 27.426 | 51.437 |
| Mixed vertex+edge insert | 1,000 | 30.819 | 23.510 | **13.336** | 279.704 | 3354.140 |
| Repeated mixed batches (5x) | 100 | 32.221 | 35.181 | **11.456** | 149.935 | 263.976 |
| Repeated mixed batches (5x) | 1,000 | 154.891 | 113.940 | **66.612** | 1428.239 | 17285.768 |

Sustained-write interpretation: Memgraph is the first candidate for ingestion-heavy services. Neo4j is the safer default when operational maturity, tooling, and long-term support dominate. FalkorDB should not be chosen for edge-heavy ingestion without a dedicated workload proof. AGE remains best justified by PostgreSQL consolidation, not by raw write latency.

## Domain Workload Result

![Graph domain workload Testcontainers benchmark](../docs/images/readme-charts/graph-domain-workload-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, two warmup iterations, four two-second measurement iterations, real Neo4j and Memgraph Testcontainers plus in-memory TinkerGraph, May 21, 2026. All values are `ms/op`; lower is better.

| Workload | TinkerGraph | Neo4j | Memgraph |
|---|---:|---:|---:|
| Code dependency traversal | **0.009** | 0.530 | 0.306 |
| Code reverse dependency lookup | **0.006** | 0.579 | 0.348 |
| Fraud high-degree neighborhood | **0.038** | 1.119 | 0.620 |
| Fraud suspicious path exists | 1.000 | 0.509 | **0.405** |
| IAM permission reachability | **0.033** | 0.508 | 0.300 |
| Social high fan-out expansion | **0.030** | 0.991 | 0.549 |
| Social two-hop candidate lookup | **0.164** | 2.756 | 1.755 |

Domain-workload interpretation: TinkerGraph is best for local and CI analysis fixtures. Memgraph is the faster persistent candidate in this local domain matrix. Neo4j remains the default production recommendation when the decision is driven by operational maturity, tooling, and maintainability.

## 10k Sustained Write Result

![Graph write ingestion 10k Testcontainers benchmark](../docs/images/readme-charts/graph-write-ingestion-10k-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, 10,000-row write batches, real Neo4j and Memgraph Testcontainers plus in-memory TinkerGraph, May 21, 2026. All values are `ms/op`; lower is better.

| Scenario | TinkerGraph | Neo4j | Memgraph |
|---|---:|---:|---:|
| Vertex-only batch insert | 71.152 | 67.135 | **53.518** |
| Edge-only batch insert | 161.907 | 108.949 | **76.105** |
| Mixed vertex+edge insert | 272.236 | 203.669 | **134.994** |
| Repeated mixed batches (5x) | 1243.229 | 919.678 | **647.574** |

10k-write interpretation: Memgraph remains the first latency candidate for sustained ingestion at larger local batch size. Neo4j is slower but remains viable when operational maturity matters more than peak ingestion speed.

## Quick Recommendation

| Situation | Recommended solution | Why |
|---|---|---|
| Unit tests, examples, local algorithm experiments | TinkerGraph + Sync API | Fastest in-memory latency and no external service. Not persistent or distributed. |
| Default production graph service | Neo4j + Sync or Virtual Thread API | Best maturity, operational tooling, ACID semantics, and broad ecosystem. Benchmark latency is not the lowest, but risk is lowest for general production. |
| Real-time low-latency graph analytics or write-heavy ingestion | Memgraph + Virtual Thread API | Strong container-backed benchmark results: fastest `medium` batch insert and fastest sustained write ingestion rows among persistent backends. |
| Existing PostgreSQL-first platform with moderate graph needs | Apache AGE + Sync API | Reuses PostgreSQL operations and data governance. Choose for platform consolidation, not raw graph latency. |
| Redis/FalkorDB-aligned stack with simple read-mostly graph queries | FalkorDB + Sync or Virtual Thread API | Good shortest-path result in the current small run, but slow edge-heavy sustained writes. Validate write workload before choosing it. |
| Kotlin coroutine application where graph calls are part of a larger suspend pipeline | Coroutine API | Best integration with existing coroutine structure. It is not automatically faster for in-memory graph work. |
| File interchange or backups | Jackson3 NDJSON or CSV | Fast, stream-friendly, and simple. Prefer GraphML only when standards/tool compatibility matters. |

## By Service Scale

| Scale | Typical shape | Backend | API model | graph-io |
|---|---|---|---|---|
| Prototype / demo | Single process, disposable data, CI examples | TinkerGraph | Sync | CSV or Jackson3 NDJSON |
| Small internal service | Tens of thousands to low millions of nodes, limited team | Neo4j when persistent; TinkerGraph only for ephemeral use | Sync for simple blocking service; Virtual Thread for blocking fan-out | Jackson3 NDJSON |
| Low-latency operational service | User-facing reads, path lookup, recommendation candidate generation | Neo4j for maturity; Memgraph when latency and ingestion dominate | Virtual Thread for blocking driver calls; Coroutine only in coroutine-native stacks | Jackson3 NDJSON or CSV |
| Real-time analytics / streaming | Frequent inserts, event-derived relationships, online exploration | Memgraph | Virtual Thread | NDJSON for append/stream style |
| PostgreSQL-centered enterprise | Existing PG backups, access control, monitoring, and DBA ownership | AGE | Sync | CSV or Jackson3 NDJSON |
| Large regulated production | Strict backup, auditing, access control, long-term maintenance | Neo4j first, AGE when PG consolidation is mandatory | Sync or Virtual Thread | Jackson3 NDJSON plus domain-specific export validation |

## By Data Scale

| Data scale | Recommendation | Notes |
|---|---|---|
| Up to about 10k nodes in tests | TinkerGraph | Current benchmark fixture directly covers this range. |
| 10k to low millions | Neo4j or Memgraph | The `medium` run supports this split: Neo4j for lower operational risk, Memgraph for lower write latency. Add domain queries before final production choice. |
| Write-heavy growing graph | Memgraph candidate first | The sustained write run shows Memgraph leading all 100 and 1,000-row latency rows. Confirm with realistic edge fan-out and transaction size. |
| PostgreSQL-owned data with graph as secondary index | AGE | Accept slower graph-native latency in exchange for operational consolidation. |
| Very large or sharded graph | Not decided by current benchmarks | Add a production-shaped benchmark. The current modules do not prove horizontal scale behavior. |

## By Domain

| Domain | Best initial choice | Reasoning |
|---|---|---|
| Social graph, follow graph, recommendation candidates | Neo4j for general production; Memgraph for real-time updates | Mature traversal/query support vs low-latency ingestion. |
| Fraud detection and transaction relationship exploration | Neo4j or Memgraph | Neo4j for ecosystem and operational maturity; Memgraph for streaming/low-latency analysis. |
| IAM, RBAC, organization graph | Neo4j | Correctness, auditing, and maintainability are more important than small-run latency. |
| Knowledge graph and entity linking | Neo4j | Query tooling and ecosystem matter. Validate if AGE is needed for PostgreSQL co-location. |
| Code graph, architecture analysis, local agent tools | TinkerGraph for local analysis; Neo4j for shared persisted service | In-memory speed is useful for local runs; shared service needs persistence and query tooling. |
| PostgreSQL product feature with a graph submodule | AGE | Avoids introducing a separate DB when graph load is moderate. |
| Cache-like graph lookup in Redis-centered operations | FalkorDB candidate | Validate writes and operational constraints because current batch insert result is weak. |

## API Model Choice

| API model | Choose when | Avoid when |
|---|---|---|
| Sync | Single request does one graph operation and the app is thread-per-request or batch-oriented | High fan-out blocking calls on platform threads |
| Virtual Thread | Blocking DB drivers are used under high concurrency | Pure CPU-bound in-memory work where launch overhead dominates |
| Coroutine | The surrounding app is already coroutine-native and graph calls are composed with other suspend work | Expecting coroutine wrappers alone to make synchronous in-memory graph calls faster |

Current `ApiModelBenchmark` result supports this: Sync was fastest for single in-memory PageRank/BFS, Virtual Thread was slightly better than Coroutine for 100-way BFS, and Coroutine had lower pure 100-way launch cost.

Production concurrency rerun, measured with five warmup iterations and ten three-second measurement iterations:

![API model production benchmark](../docs/images/readme-charts/graph-api-model-production-chart-01.png)

| Scenario | Concurrency 10 | Concurrency 100 | Concurrency 1,000 |
|---|---:|---:|---:|
| BFS with Virtual Threads | **31.151 us/op** | **142.730 us/op** | **921.873 us/op** |
| BFS with Coroutines | 38.795 us/op | 157.676 us/op | 1151.154 us/op |
| Virtual Thread creation | 8.867 us/op | 28.225 us/op | 200.810 us/op |
| Coroutine launch | **0.586 us/op** | **4.826 us/op** | **47.657 us/op** |

This keeps the recommendation unchanged: choose Virtual Threads for blocking graph driver concurrency, and choose Coroutines for coroutine-native composition where launch overhead and structured concurrency matter.

## graph-io Format Choice

| Format | Best use | Caution |
|---|---|---|
| CSV | Fast simple export/import and spreadsheet-friendly inspection | Less expressive for nested properties and schema evolution |
| Jackson3 NDJSON | Default service interchange format | Keep streaming and buffering enabled |
| Jackson2 NDJSON | Compatibility with older Jackson2 stacks | Prefer Jackson3 for new modules when possible |
| GraphML | Interop with graph tools that require GraphML | XML overhead is higher; factory caching and buffered streams are mandatory |
| Okio variants | Kotlin/Okio-heavy pipelines | Validate against the exact target sink/source |

## When To Add More Benchmark Data

Add another benchmark run before deciding when any of these are true:

- The graph has more than low millions of nodes or high edge fan-out.
- The service depends on multi-hop traversal beyond the current `maxDepth` fixtures.
- Writes happen in large transactions or sustained streams.
- The backend must meet strict P99 latency, recovery, or memory limits.
- The decision affects infrastructure cost or operational ownership.

Tracked follow-up benchmark issues:

- [#197 Benchmark: graph workload shapes by domain and fan-out](https://github.com/bluetape4k/bluetape4k-graph/issues/197)
- [#199 Benchmark: production-grade API model latency and allocation](https://github.com/bluetape4k/bluetape4k-graph/issues/199)
- [#201 Benchmark: selective 10k sustained graph ingestion profiles](https://github.com/bluetape4k/bluetape4k-graph/issues/201)

Suggested next benchmark targets:

- Add #197 domain workloads for social, fraud, and knowledge graph fan-out shapes.
- Re-run #199 API model latency and allocation with production-grade windows.
- Add #201 selective 10k sustained ingestion profiles for backend subsets that can complete in a useful local window.

Use the existing Gradle benchmark tasks as the primary execution path:

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark
./gradlew :graph-benchmark:mainApiModelProductionBenchmark
./gradlew :graph-benchmark:mainGraphWriteIngestion10kBenchmark
```

## Final Selection Rule

Start with the lowest-risk backend that matches the service shape:

1. Use TinkerGraph only for tests, local analysis, and prototypes.
2. Use Neo4j as the default production graph database.
3. Prefer Memgraph when low-latency ingestion and real-time graph analytics are the main product requirement.
4. Use AGE when PostgreSQL consolidation is more important than graph-native latency.
5. Use FalkorDB only after validating the exact read/write mix.

Then choose the API model by runtime: Sync for simple direct calls, Virtual Threads for blocking driver concurrency, Coroutine for coroutine-native composition.

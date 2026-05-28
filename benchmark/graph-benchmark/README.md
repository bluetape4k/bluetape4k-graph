# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

kotlinx-benchmark module for graph performance comparison. It now contains eight benchmark tracks:

- Existing TinkerGraph sync vs virtual-thread graph operations.
- TinkerGraph API model comparison across sync, virtual-thread, and coroutine APIs.
- Graph database backend comparison through the shared `GraphOperations` contract.
- Domain-shaped graph workload comparison for social, IAM, fraud, and code graph queries.
- Sustained graph write and batch ingestion comparison through the shared `GraphOperations` contract.
- Production-shaped API model comparison across 10, 100, and 1,000 concurrent units.
- PostgreSQL abuser detection comparison across AGE + Exposed, Exposed JDBC, and JPA/Hibernate.
- graph-io format comparison using the same generated TinkerGraph dataset.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## What It Measures

- `GraphDbComparisonBenchmark`: `tinkergraph`, `neo4j`, `memgraph`, `age`, and `falkordb` backends.
- `GraphDomainWorkloadBenchmark`: social high fan-out, IAM reachability, fraud path, and code dependency workloads on `tinkergraph`, `neo4j`, and `memgraph`.
- `GraphWriteIngestionBenchmark`: vertex-only, edge-only, mixed, and repeated mixed write batches on the same backend matrix.
- `AbuserDetectionBenchmark`: PostgreSQL `age`, `exposed`, and `jpa` abuser-detection backends over one deterministic account-signal fixture.
- `GraphIoComparisonBenchmark`: `csv`, `jackson2`, `jackson3`, `graphml`, `okio-jackson3`, and `okio-graphml`.
- `ApiModelBenchmark`: sync, virtual-thread, and coroutine API overhead on the same in-memory TinkerGraph fixture.
- Legacy operation benchmarks: batch insert, shortest path, neighbors, traversal, algorithm, and vertex operations.

Container-backed backend benchmarks use bluetape4k Testcontainers launchers or wrappers. Run them serially and expect longer startup time.

## Running

```bash
./gradlew :graph-benchmark:benchmark
```

kotlinx-benchmark writes JMH JSON under `benchmark/graph-benchmark/build/reports/benchmarks/**/main.json`. Use the Gradle tasks as the primary entrypoint; raw JMH jar execution is only for local diagnostics.

Graph DB backend matrix:

```bash
./gradlew :graph-benchmark:mainGraphDbSmallBenchmark
./gradlew :graph-benchmark:mainGraphDbMediumBenchmark
```

Domain-shaped workload matrix:

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark
```

Sustained write ingestion profiles:

```bash
./gradlew :graph-benchmark:mainGraphWriteIngestion10kBenchmark
```

Docker-free API model production matrix:

```bash
./gradlew :graph-benchmark:mainApiModelProductionBenchmark
```

PostgreSQL abuser detection smoke and comparison matrix:

```bash
./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark
./gradlew :graph-benchmark:abuserDetectionBenchmark
```

The smoke task runs `sizeName=smoke` and `scenarioName=shared`. The comparison task runs `small` and `medium` datasets across `shared`, `transfer`, `noisy-dense`, and `wide-fanout`. A manual `large` fixture exists for local stress runs when the useful question is how latency changes as account count and inspection edges grow.

## Latest API Model Result

![API model benchmark](../../docs/images/readme-charts/graph-api-model-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, TinkerGraph fixture, May 21, 2026. This is a short local smoke run; use the raw JSON and rerun before treating the ranking as a release-grade claim.

PageRank throughput uses `ops/s`; higher is better.

| API model | Score | Error | Allocation |
|---|---:|---:|---:|
| Sync | **138,943.484 ops/s** | ±40,362.146 | 28,451 B/op |
| Virtual Thread | 40,283.460 ops/s | ±9,678.720 | 29,456 B/op |
| Coroutine Flow | 36,879.554 ops/s | ±85,084.781 | 29,516 B/op |

BFS and launch/create rows use `us/op`; lower is better.

| Scenario | API model | Score | Error | Allocation |
|---|---|---:|---:|---:|
| BFS depth=5 | Sync | **4.724 us/op** | ±3.022 | 21,990 B/op |
| BFS depth=5 | Virtual Thread | 18.668 us/op | ±8.229 | 23,152 B/op |
| BFS depth=5 | Coroutine Flow | 20.244 us/op | ±11.268 | 23,455 B/op |
| BFS 100-way | Virtual Thread | **240.903 us/op** | ±167.502 | 2,318,801 B/op |
| BFS 100-way | Coroutine async | 279.828 us/op | ±329.942 | 2,367,754 B/op |
| 100-way launch/create | Virtual Thread | 51.042 us/op | ±173.745 | 61,464 B/op |
| 100-way launch/create | Coroutine async | **5.916 us/op** | ±3.127 | 28,373 B/op |

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-api-model-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-api-model-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/2026-05-21-api-model-jmh.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-api-model-results.md)

## Latest API Production Result

![API model production benchmark](../../docs/images/readme-charts/graph-api-model-production-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, five warmup iterations, ten three-second measurement iterations, TinkerGraph fixture, May 21, 2026. All values are `us/op`; lower is better.

| Scenario | Concurrency 10 | Concurrency 100 | Concurrency 1,000 |
|---|---:|---:|---:|
| BFS with Virtual Threads | **31.151** | **142.730** | **921.873** |
| BFS with Coroutines | 38.795 | 157.676 | 1151.154 |
| Virtual Thread creation | 8.867 | 28.225 | 200.810 |
| Coroutine launch | **0.586** | **4.826** | **47.657** |

Interpretation: Virtual Threads remain the lower-latency choice for blocking-style concurrent graph work in this fixture. Coroutines have much lower launch overhead, so choose them when the surrounding application is already coroutine-native or the graph call is composed with other suspend work.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-api-model-production-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-api-model-production-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/api-model-production-gradle-2026-05-21.json)

## Latest Abuser Detection Result

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, one warmup iteration, one one-second measurement iteration, PostgreSQL AGE Testcontainer, `smoke` fixture with 120 accounts, `shared` scenario, May 28, 2026. All latency values are `ms/op`; lower is better. Detection quality was verified by smoke tests before the benchmark: precision `1.0`, recall `1.0`, and F1 `1.0` for AGE + Exposed, Exposed JDBC, and JPA/Hibernate.

Scenario matrix:

| Scenario | Shape |
|---|---|
| `shared` | shared device/IP/payment dominated signal graph |
| `transfer` | deeper transfer-chain dominated signal graph |
| `noisy-dense` | high background edge volume and denser inspection set |
| `wide-fanout` | many direct suspicious neighbors per known abusive account |

| Benchmark | AGE + Exposed | Exposed JDBC | JPA/Hibernate |
|---|---:|---:|---:|
| `detectCandidates` | 13.002 | **0.199** | 0.210 |
| `detectF1BasisPoints` | 11.533 | **0.197** | 0.202 |

Interpretation: this smoke run proves the contract and benchmark execution surface. AGE currently pays graph abstraction and traversal cost, while the relational baselines execute the shared recursive SQL query directly. Treat this as smoke evidence; use the `small` and `medium` scenario matrix before making release-grade ranking claims.

Artifacts:

- [Raw JMH JSON](../../docs/benchmark/2026-05-28-abuser-detection-smoke-main.json)
- [Markdown result table](../../docs/benchmark/2026-05-28-abuser-detection-smoke-results.md)

## Latest Testcontainers Result

### Domain Workloads

![Graph domain workload Testcontainers benchmark](../../docs/images/readme-charts/graph-domain-workload-testcontainers-latency-chart-01.png)

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

Interpretation: TinkerGraph is still the fastest local analysis engine for in-memory domain fixtures. Among persistent backends, Memgraph is consistently faster than Neo4j in this local domain matrix, while Neo4j remains the safer default when ecosystem, operations, and query tooling dominate.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-domain-workload-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-domain-workload-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json)

### Sustained Write Ingestion

![Graph write ingestion Testcontainers benchmark](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, real Testcontainers backends, GC profiler enabled, May 21, 2026. `Repeated mixed batches` runs five mixed vertex+edge batches in one benchmark operation.

All values are `ms/op`; lower is better. Bold indicates the fastest backend in this run.

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

Interpretation: Memgraph is the fastest persistent backend across every latency row. Neo4j remains the lower-risk production default when operational maturity matters more than raw ingestion latency. FalkorDB is competitive for vertex-only insertion, but edge-heavy and repeated mixed write profiles are too slow in this contract benchmark. The full 10k matrix is tracked separately in [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201) because FalkorDB already reached 17.286 s/op at the 1,000-row repeated mixed profile.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-write-ingestion-testcontainers-results.md)

### 10k Sustained Write Ingestion

![Graph write ingestion 10k Testcontainers benchmark](../../docs/images/readme-charts/graph-write-ingestion-10k-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, 10,000-row write batches, real Neo4j and Memgraph Testcontainers plus in-memory TinkerGraph, May 21, 2026. All values are `ms/op`; lower is better. AGE and FalkorDB are excluded from this selective 10k run because the 1,000-row repeated mixed profile already showed multi-second latency and the useful local window was reserved for the three fastest operational candidates.

| Scenario | TinkerGraph | Neo4j | Memgraph |
|---|---:|---:|---:|
| Vertex-only batch insert | 71.152 | 67.135 | **53.518** |
| Edge-only batch insert | 161.907 | 108.949 | **76.105** |
| Mixed vertex+edge insert | 272.236 | 203.669 | **134.994** |
| Repeated mixed batches (5x) | 1243.229 | 919.678 | **647.574** |

Interpretation: Memgraph remains the best latency candidate for large sustained ingestion. Neo4j is slower but still viable when operational maturity matters more than peak ingestion speed.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-write-ingestion-10k-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-write-ingestion-10k-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-write-ingestion-10k-testcontainers-2026-05-21.json)

### Medium Dataset

![Graph DB medium Testcontainers benchmark](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, three warmup iterations, five three-second measurement iterations, `medium` dataset, May 21, 2026. FalkorDB used a 60 second Jedis read timeout in the benchmark driver because the default `jfalkordb` timeout failed on this fixture.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 44.967 | 15.690 | **11.364** | 309.090 | 1929.180 |
| `countPersons` | 0.308 | 0.528 | 1.341 | 2.176 | **0.197** |
| `oneHopNeighbors` | **0.003** | 0.665 | 0.308 | 10.175 | 1.046 |
| `shortestPath` | **0.019** | 0.700 | 0.386 | 12.420 | 0.512 |

All values are `ms/op`; lower is better. Bold indicates the fastest backend in this run.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json)
- [FalkorDB timeout rerun JSON](../../docs/benchmark/graph-db-medium-falkordb-testcontainers-2026-05-21.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-db-medium-testcontainers-results.md)

### Small Dataset

![Graph DB Testcontainers benchmark](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, `small` dataset, May 21, 2026.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 5.704 | 6.903 | **1.954** | 21.580 | 38.670 |
| `countPersons` | **0.030** | 0.779 | 0.394 | 0.645 | 0.195 |
| `oneHopNeighbors` | **0.004** | 0.807 | 0.346 | 0.941 | 0.639 |
| `shortestPath` | **0.022** | 0.795 | 0.344 | 1.279 | 0.290 |

All values are `ms/op`; lower is better. Bold indicates the fastest backend in this run.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-db-small-gradle-testcontainers-2026-05-21.json)
- [Legacy raw JMH JSON](../../docs/benchmark/graph-db-testcontainers-2026-05-21.json)
- [Normalized baseline JSON](../../docs/benchmark/graph-benchmark-baseline.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-db-testcontainers-results.md)

## Reports

Normalize JMH JSON into a stable before/after report:

```bash
python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py \
  benchmark/graph-benchmark/build/reports/benchmarks/main/main.json \
  --markdown docs/benchmark/graph-benchmark-latest.md
```

When comparing a candidate against a baseline:

```bash
python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py candidate.json \
  --baseline baseline.json \
  --metric score \
  --direction lower_is_better \
  --markdown docs/benchmark/graph-benchmark-candidate.md
```

Render the graph DB backend chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_graph_db_backend_chart.py \
  docs/benchmark/graph-db-small-gradle-testcontainers-2026-05-21.json
```

Render the API model chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_api_model_chart.py \
  docs/benchmark/2026-05-21-api-model-jmh.json
```

Render the API production chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_api_model_production_chart.py \
  docs/benchmark/api-model-production-gradle-2026-05-21.json
```

Render the domain workload chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_graph_domain_workload_chart.py \
  docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json
```

Render the sustained write ingestion chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_graph_write_ingestion_chart.py \
  docs/benchmark/graph-write-ingestion-10k-testcontainers-2026-05-21.json
```

## Self-Improve Gate

Use `bluetape4k-self-improve` only after a fresh baseline exists. Sealed files for optimization rounds are:

- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDbComparisonBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDomainWorkloadBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphWriteIngestionBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphIoComparisonBenchmark.kt`
- `benchmark/graph-benchmark/scripts/normalize_jmh_report.py`
- `benchmark/graph-benchmark/scripts/render_graph_domain_workload_chart.py`
- `benchmark/graph-benchmark/scripts/render_graph_write_ingestion_chart.py`
- `docs/benchmark/graph-benchmark-baseline.json`

Run the sealed-file validator before accepting a candidate:

```bash
scripts/validate-graph-benchmark-sealed.sh
```

## Notes

- Amazon Neptune is intentionally out of scope until reliable local/integration testability is available.
- graph DB benchmarks compare the shared repository contract, not vendor-specific tuned query APIs.

# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH/kotlinx-benchmark module for graph performance comparison. It now contains four benchmark tracks:

- Existing TinkerGraph sync vs virtual-thread graph operations.
- TinkerGraph API model comparison across sync, virtual-thread, and coroutine APIs.
- Graph database backend comparison through the shared `GraphOperations` contract.
- graph-io format comparison using the same generated TinkerGraph dataset.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## What It Measures

- `GraphDbComparisonBenchmark`: `tinkergraph`, `neo4j`, `memgraph`, `age`, and `falkordb` backends.
- `GraphIoComparisonBenchmark`: `csv`, `jackson2`, `jackson3`, `graphml`, `okio-jackson3`, and `okio-graphml`.
- `ApiModelBenchmark`: sync, virtual-thread, and coroutine API overhead on the same in-memory TinkerGraph fixture.
- Legacy operation benchmarks: batch insert, shortest path, neighbors, traversal, algorithm, and vertex operations.

Container-backed backend benchmarks use bluetape4k Testcontainers singleton launchers. Run them serially and expect longer startup time.

## Running

```bash
./gradlew :graph-benchmark:benchmark
```

kotlinx-benchmark writes JMH JSON under `benchmark/graph-benchmark/build/reports/benchmarks/**/main.json`.

For the graph DB backend matrix, run the real Testcontainers-backed JMH target:

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*GraphDbComparisonBenchmark.*' \
  -wi 1 -i 3 -r 1s -w 1s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p sizeName=small \
  -rf json \
  -rff docs/benchmark/graph-db-testcontainers-2026-05-21.json
```

For the medium backend matrix:

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*GraphDbComparisonBenchmark.*' \
  -wi 3 -i 5 -r 3s -w 2s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p sizeName=medium \
  -rf json \
  -rff docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json
```

For the Docker-free API model matrix:

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*ApiModelBenchmark.*' \
  -wi 1 -i 3 -r 1s -w 1s -f 1 \
  -prof gc \
  -rf json \
  -rff docs/benchmark/2026-05-21-api-model-jmh.json
```

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

## Latest Testcontainers Result

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
| `batchInsertCycle` | 5.379 | 6.217 | **1.969** | 21.665 | 38.660 |
| `countPersons` | **0.032** | 0.809 | 0.402 | 0.610 | 0.193 |
| `oneHopNeighbors` | **0.003** | 0.811 | 0.334 | 0.932 | 0.708 |
| `shortestPath` | **0.018** | 0.806 | 0.331 | 1.320 | 0.280 |

All values are `ms/op`; lower is better. Bold indicates the fastest backend in this run.

Artifacts:

- [Chart PNG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-db-testcontainers-2026-05-21.json)
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
  docs/benchmark/graph-db-testcontainers-2026-05-21.json
```

Render the API model chart used above:

```bash
python3 benchmark/graph-benchmark/scripts/render_api_model_chart.py \
  docs/benchmark/2026-05-21-api-model-jmh.json
```

## Self-Improve Gate

Use `bluetape4k-self-improve` only after a fresh baseline exists. Sealed files for optimization rounds are:

- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDbComparisonBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphIoComparisonBenchmark.kt`
- `benchmark/graph-benchmark/scripts/normalize_jmh_report.py`
- `docs/benchmark/graph-benchmark-baseline.json`

Run the sealed-file validator before accepting a candidate:

```bash
scripts/validate-graph-benchmark-sealed.sh
```

## Notes

- Amazon Neptune is intentionally out of scope until reliable local/integration testability is available.
- graph DB benchmarks compare the shared repository contract, not vendor-specific tuned query APIs.

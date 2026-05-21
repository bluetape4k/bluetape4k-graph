# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH/kotlinx-benchmark module for graph performance comparison. It now contains three benchmark tracks:

- Existing TinkerGraph sync vs virtual-thread graph operations.
- Graph database backend comparison through the shared `GraphOperations` contract.
- graph-io format comparison using the same generated TinkerGraph dataset.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## What It Measures

- `GraphDbComparisonBenchmark`: `tinkergraph`, `neo4j`, `memgraph`, `age`, and `falkordb` backends.
- `GraphIoComparisonBenchmark`: `csv`, `jackson2`, `jackson3`, `graphml`, `okio-jackson3`, and `okio-graphml`.
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

## Latest Testcontainers Result

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

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

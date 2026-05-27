# graph-io-benchmark

[English](README.md) | [한국어](README.ko.md)

JMH benchmarks for bulk graph import/export formats and I/O adapters.

## Architecture

![graph-io-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-io-benchmark-architecture-01.png)

## What It Measures

`graph-io-benchmark` measures graph bulk I/O over an in-memory TinkerGraph dataset and temporary files.

- CSV, Jackson 2 NDJSON, Jackson 3 NDJSON, and GraphML export/import/round-trip paths.
- Sync, virtual-thread, and coroutine bulk I/O adapters where supported.
- OkIO-backed Jackson 3 and GraphML paths, including GZIP NDJSON.
- Dataset sizes selected by the JMH `sizeName` parameter: `small` and `medium`.

## Source Evidence

- `build.gradle.kts` depends on `graph-io-core`, `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml`, `graph-okio`, `graph-tinkerpop`, coroutines, and virtual-thread support.
- `BulkGraphIoBenchmarkState` creates a temporary directory, builds a TinkerGraph dataset, and tears the directory down after the trial.
- `BulkGraphIoBenchmark` covers java.io-style CSV, NDJSON, and GraphML sync, virtual-thread, coroutine, import, export, and round-trip paths.
- `OkioGraphIoBenchmark` covers OkIO path sinks/sources, Jackson 3 NDJSON, GZIP NDJSON, GraphML, and virtual-thread OkIO adapters.

## Running

```bash
./gradlew :graph-io-benchmark:benchmark
```

The benchmark writes temporary files during each trial and removes them in teardown.

For a fast wiring check that runs a tiny dataset and emits a normal kotlinx-benchmark JSON report, use:

```bash
./gradlew :graph-io-benchmark:smokeBenchmark
```

The smoke configuration compares representative CSV, Jackson 3 OkIO, and GraphML OkIO round-trip paths with `sizeName=smoke`.
Use it as CI evidence that benchmark methods and report generation still work, not as performance data.
The generated JSON report is under `benchmark/graph-io-benchmark/build/reports/benchmarks/smoke/*/main.json`.

## Latest Results

The latest published `small` dataset result is from `docs/benchmark/2026-04-18-graph-io-bulk-results.md`.
All values are `ms/op`; lower is better.

### Export

| Format | Sync | VirtualThread | Suspend |
|---|---:|---:|---:|
| CSV | **1.017** | 1.185 | 1.477 |
| Jackson2 NDJSON | **1.194** | 1.221 | 1.318 |
| Jackson3 NDJSON | **1.275** | 1.300 | 1.329 |
| GraphML | 2.582 | 4.192 | **2.455** |

![Graph-IO export latency chart](../../docs/images/readme-charts/graph-io-export-latency-chart-01.png)

### Import

| Format | Sync | VirtualThread | Suspend |
|---|---:|---:|---:|
| CSV | 17.854 | **17.624** | 23.393 |
| Jackson2 NDJSON | 18.831 | **18.120** | 151.415 |
| Jackson3 NDJSON | 19.852 | **19.302** | 155.279 |
| GraphML | 21.111 | **21.095** | 22.380 |

![Graph-IO import latency chart](../../docs/images/readme-charts/graph-io-import-latency-chart-01.png)

### Round Trip

| Format | Sync | VirtualThread | Suspend |
|---|---:|---:|---:|
| CSV | 19.752 | **17.629** | 18.512 |
| Jackson2 NDJSON | 18.880 | **18.677** | 151.615 |
| Jackson3 NDJSON | 19.142 | **18.956** | 164.172 |
| GraphML | 21.707 | 21.450 | **21.236** |

![Graph-IO round-trip latency chart](../../docs/images/readme-charts/graph-io-roundtrip-latency-chart-01.png)

Interpretation:

- CSV and Jackson NDJSON export are close, around 1-1.5 ms/op on the small dataset.
- Import and round-trip are dominated by TinkerGraph vertex/edge creation, not parser choice.
- Jackson2/3 suspend import has an outlier caused by coroutine dispatcher setup in this quick-run benchmark shape; production coroutine contexts should be measured separately.
- GraphML needs cached XML factories and buffered I/O to stay in the 2-22 ms/op range.

## Notes

- Use this module when changing graph bulk I/O serializers, importers, exporters, or OkIO integration.
- The module does not start external graph database containers; the workload is file I/O plus in-memory graph operations.

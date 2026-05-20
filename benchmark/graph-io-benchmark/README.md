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

## Notes

- Use this module when changing graph bulk I/O serializers, importers, exporters, or OkIO integration.
- The module does not start external graph database containers; the workload is file I/O plus in-memory graph operations.

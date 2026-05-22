# Issue 197/199/201 Gradle Benchmark Lessons

## Context

Issues #197, #199, and #201 added graph-benchmark coverage for domain workload shapes, production-grade API model latency, and selective 10k sustained ingestion. The initial implementation drifted toward direct JMH jar commands and chart styles that did not match the existing README assets.

## Decision

Use `kotlinx-benchmark` Gradle tasks as the primary benchmark entrypoint:

- `:graph-benchmark:mainGraphDomainWorkloadBenchmark`
- `:graph-benchmark:mainApiModelProductionBenchmark`
- `:graph-benchmark:mainGraphWriteIngestion10kBenchmark`

Keep raw JMH jar execution only as a local diagnostic escape hatch. Testcontainers-backed runs stay serial and store raw JSON under `docs/benchmark/`.

## Outcome

Fresh local benchmark artifacts were generated for the small DB matrix, domain workload matrix, selective 10k write ingestion, and API production matrix. README files now include tables plus SVG/PNG charts with the existing hand-drawn README chart font convention: `Architects Daughter` for titles and `Comic Sans MS` / `Comic Neue` / `Chalkboard SE` fallbacks for body text.

## Verification

- `./gradlew :graph-benchmark:mainGraphDbSmallBenchmark --no-build-cache`
- `./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark --no-build-cache`
- `./gradlew :graph-benchmark:mainGraphWriteIngestion10kBenchmark --no-build-cache`
- `./gradlew :graph-benchmark:mainApiModelProductionBenchmark --no-build-cache`
- `python3 -m py_compile` for graph-benchmark chart scripts

## Future Guidance

For graph benchmarks, add a named `kotlinx.benchmark` Gradle configuration first, then document that Gradle task in both English and Korean README files. Reuse existing README chart font/style before creating new SVG/PNG assets.

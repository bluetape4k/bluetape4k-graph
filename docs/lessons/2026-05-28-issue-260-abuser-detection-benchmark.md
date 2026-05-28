# Issue #260 Abuser Detection Benchmark

## Context

Epic #260 needed a PostgreSQL-only benchmark comparing AGE + Exposed, Exposed JDBC, and JPA/Hibernate for abuser detection.

## Decision

Keep the workload in `benchmark/graph-benchmark`, add a shared fixture/metric contract, and expose the run through `kotlinx-benchmark` Gradle tasks. Use smoke tests to prove detection quality before treating latency output as meaningful.

## Outcome

Added deterministic `smoke`/`small`/`medium`/`large` fixtures, scenario shapes (`shared`, `transfer`, `noisy-dense`, `wide-fanout`), three backend engines, `AbuserDetectionBenchmark`, smoke/comparison benchmark Gradle configurations, README documentation, and raw smoke JSON evidence.

## Verification

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin --no-build-cache`
- `./gradlew :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionContractTest" --no-build-cache`
- `./gradlew :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionEngineSmokeTest" --no-build-cache`
- `./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark --no-build-cache`

## Future Guard

For graph benchmark comparisons, always publish the `kotlinx-benchmark` task, raw JSON path, run conditions, metric direction, scenario/size matrix, and separate detection-quality evidence from latency ranking.

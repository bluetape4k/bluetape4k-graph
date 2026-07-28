# 이슈 216 benchmark summary schema

## 맥락

Issue #216 required the standalone AGE and Neo4j benchmark wrapper to expose a documented shared report shape while keeping existing benchmark commands usable.

## 결정

`scripts/benchmark-neo4j-age.sh` now keeps the compact `primary` and `sub_scores` contract, then adds `schema`, `unit`, `direction`, `sources`, and detailed `benchmarks` rows. The wrapper normalizes all latency values to `us/op` and fails early on missing, malformed, empty, non-finite, or unsupported JMH result data.

## 결과

The benchmark guide documents the wrapper command, report roots, stable JSON schema, metric direction, and failure behavior in both English and Korean.

## 검증

- `bash -n scripts/benchmark-neo4j-age.sh`
- `git diff --check`
- `./gradlew :graph-benchmark:test --tests 'io.bluetape4k.graph.benchmark.BenchmarkScriptContractTest' --no-daemon`

## 향후 메모

Keep wrapper stdout parseable as exactly one final JSON line. Send Gradle logs and diagnostics to stderr so ranking automation can read stdout without filtering.

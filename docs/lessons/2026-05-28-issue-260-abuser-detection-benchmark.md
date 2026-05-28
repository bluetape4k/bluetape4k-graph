# Issue #260 PostgreSQL Traversal Benchmark

## Context

Epic #260 started as an abuser-detection comparison, but the stronger GraphDB adoption question is variable-depth traversal. The final slice keeps bounded fraud detection as a secondary scenario and makes authorization inheritance the primary measured workload.

## Decision

Use `benchmark/graph-benchmark` and `kotlinx-benchmark`. Compare AGE/Cypher with PostgreSQL recursive CTE and iterative traversal on a deterministic authorization inheritance fixture. Keep correctness evidence separate from latency ranking.

## Outcome

Added authorization inheritance fixtures, oracle, AGE/Cypher engine, PostgreSQL CTE and iterative engines, benchmark tasks, measured result JSON, Markdown tables, and README chart assets. Fraud detection was also tightened to bounded, time-windowed, risk-filtered traversal with CTE/iterative relational splits.

Measured authorization inheritance results did not support a speed-based AGE adoption claim. PostgreSQL CTE and iterative traversal were faster across the documented `small` and `medium` matrix; AGE remains an expressiveness candidate, not the latency winner for this fixture.

## Verification

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.authz.AuthzInheritanceEngineSmokeTest" --no-build-cache`
- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionContractTest" --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionEngineSmokeTest" --no-build-cache`
- `./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache`

## Future Guard

For graph benchmark comparisons, publish the `kotlinx-benchmark` task, raw JSON path, run conditions, metric direction, scenario/size matrix, chart assets, and explicit interpretation. Do not collapse recursive CTE and iterative traversal into one relational baseline, and do not claim a GraphDB win unless measured results support it.

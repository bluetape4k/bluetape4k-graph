# 이슈 #260 PostgreSQL Traversal Benchmark

## 맥락

Epic #260 started as an abuser-detection comparison, but the stronger GraphDB adoption question is variable-depth traversal. The final slice keeps bounded fraud detection as a secondary scenario and makes authorization inheritance the primary measured workload.

## 결정

Use `benchmark/graph-benchmark` and `kotlinx-benchmark`. Compare native Neo4j Cypher plus AGE/Cypher with PostgreSQL recursive CTE and iterative traversal on a deterministic authorization inheritance fixture. Keep correctness evidence separate from latency ranking.

## 결과

Added authorization inheritance fixtures, oracle, AGE/Cypher engine, PostgreSQL CTE and iterative engines, benchmark tasks, measured result JSON, Markdown tables, and README chart assets. Fraud detection was also tightened to bounded, time-windowed, risk-filtered traversal with CTE/iterative relational splits.

Measured authorization inheritance results did not support a speed-based AGE adoption claim. PostgreSQL CTE and iterative traversal were faster across the documented `small` and `medium` matrix; AGE remains an expressiveness candidate, not the latency winner for this fixture.

TinkerGraph is excluded only from this GraphDB adoption benchmark because it is in-memory. Keep existing TinkerGraph API/contract benchmark tracks separate and do not use them as persistent database adoption evidence.

The first authz matrix was still too shallow for a final adoption call. The follow-up large-data, long-path probe added `long-chain` with 10-hop traversal and `deep-wide` with 12-hop traversal on `large` data through `authzInheritanceAdoptionBenchmark`.

The adoption probe finally produced a qualified GraphDB signal: `large + long-chain` favored Neo4j Cypher at 12.731 ms/op versus PostgreSQL iterative at 47.568 ms/op and PostgreSQL CTE at 55.364 ms/op. `large + deep-wide` still favored PostgreSQL CTE at 11.596 ms/op, so the use case is not generic authorization or fraud; it is long, selective, path-shaped traversal. AGE timed out on both `large + long-chain` and `large + deep-wide`, and Memgraph terminated the Bolt connection during large fixture load in this local run.

The final adoption decision report is `docs/benchmark/2026-05-28-graphdb-adoption-decision-report.md`. Keep it as the entrypoint for this benchmark slice because it includes AGE timeout rows, Memgraph load-failure evidence, TinkerGraph scope, recommendation, artifacts, and DoD.

## 검증

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.authz.AuthzInheritanceEngineSmokeTest" --no-build-cache`
- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionContractTest" --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionEngineSmokeTest" --no-build-cache`
- `./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache`
- Direct JMH diagnostic runs for `authzInheritanceAdoptionBenchmark` backend isolation: Neo4j/PostgreSQL JSON plus AGE timeout and Memgraph failure logs under `docs/benchmark/`.

## 향후 가드

For graph benchmark comparisons, publish the `kotlinx-benchmark` task, raw JSON path, run conditions, metric direction, scenario/size matrix, chart assets, and explicit interpretation. Do not collapse recursive CTE and iterative traversal into one relational baseline, and do not claim a GraphDB win unless measured results support it.

For GraphDB adoption comparisons, exclude TinkerGraph from the decision table while leaving unrelated in-memory benchmark tracks intact. Do not stop at AGE-only results; include at least one native persistent graph backend before deciding whether the workload justifies GraphDB.

# 이슈 196 medium backend benchmark

## 맥락

Issue #196 required a medium-size `GraphDbComparisonBenchmark` run across the
local backend matrix with real Testcontainers and README table/chart evidence.

## 결정

Keep `graph-benchmark` as the execution module and add only benchmark-specific
support: package the main benchmark classes into the JMH jar, render a separate
medium chart, and run FalkorDB with a 60 second Jedis read timeout for this
fixture.

## 결과

The medium matrix produced 20 rows: five backends times four operations.
Memgraph was fastest for persistent `batchInsertCycle`, FalkorDB was fastest for
`countPersons`, and TinkerGraph remained the in-memory latency baseline. README
guidance now distinguishes Neo4j's operational maturity from Memgraph's lower
local write latency.

## 검증

- `GraphDbComparisonBenchmark` medium JMH run with `-wi 3 -i 5 -w 2s -r 3s -f 1`.
- FalkorDB medium rows rerun with a 60 second Jedis read timeout after the
  default driver timed out on the fixture.
- `docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json` contains 20
  rows with `age`, `falkordb`, `memgraph`, `neo4j`, and `tinkergraph`.
- `python3 -m py_compile benchmark/graph-benchmark/scripts/render_graph_db_backend_chart.py`.
- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --no-build-cache`.
- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:mainBenchmarkJar --no-build-cache`.
- Claude CLI review raised a FalkorDB reusable-container close check; wrapper
  inspection confirmed the benchmark-owned container handle is intentionally
  closed after the trial.
- Current session review found no actionable correctness issues.

## 향후 가드

Do not trust `mainBenchmarkJar` until it is checked for the real benchmark
classes, not only the generated JMH harness classes. For FalkorDB medium or
larger fixtures, keep the driver timeout explicit in benchmark code and document
that the result is not the default `jfalkordb` timeout profile.

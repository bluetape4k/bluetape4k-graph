# Issue 198 Sustained Write Benchmark

## Context

Issue #198 needed a real Testcontainers-backed benchmark for sustained graph writes, not only the existing small mixed `batchInsertCycle` row.

## Decision

Add `GraphWriteIngestionBenchmark` as a separate JMH target under `graph-benchmark` with vertex-only, edge-only, mixed, and repeated mixed write profiles. Keep the default parameter matrix at 100 and 1,000 row batches so routine local runs stay practical.

## Outcome

Memgraph was the fastest persistent backend across every sustained write latency row. Neo4j remains the lower-risk production default when operational maturity matters more than raw ingestion latency. FalkorDB was competitive for vertex-only insert but too slow for edge-heavy and repeated mixed writes in this contract benchmark.

The full 10,000-row backend matrix was split into follow-up issue #201 because FalkorDB already reached about 17.3 s/op at the 1,000-row repeated mixed profile.

## Verification

- Built `:graph-benchmark:mainBenchmarkJar` and verified the JMH jar contains the new benchmark classes.
- Ran a TinkerGraph smoke benchmark for all four methods.
- Added `GraphWriteIngestionSmokeTest` so CI covers the benchmark helper state and method row counts without Docker.
- Ran the real Testcontainers matrix for TinkerGraph, Neo4j, Memgraph, AGE, and FalkorDB with GC profiler enabled.
- Rendered README chart outputs as both SVG and PNG.
- Ran current-session review and Claude CLI review, then removed `!!` from new code and aligned benchmark annotations with the documented run.

## Future Guidance

For Testcontainers-backed benchmark additions, check the generated JMH fat jar for real benchmark classes as well as generated harness classes. For very slow backend profiles, prefer a separate selective matrix issue over expanding the default benchmark parameter set.

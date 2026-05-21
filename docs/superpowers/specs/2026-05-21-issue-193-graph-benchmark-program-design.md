# Issue 193 Graph Benchmark Program Design

## Context

Issue #193 needs one benchmark program that compares graph DB backends, graph-io implementations, and later self-improve before/after optimization results.

## Decision

Use the existing `benchmark/graph-benchmark` kotlinx-benchmark module as the unified entry point. Keep the existing TinkerGraph sync/virtual-thread benchmarks and add:

- `GraphDbComparisonBenchmark` for backend comparison through `GraphOperations`.
- `GraphIoComparisonBenchmark` for format comparison using the same generated TinkerGraph dataset.
- `normalize_jmh_report.py` for stable JMH JSON normalization and baseline/candidate deltas.

## Constraints

- Container-backed DB benchmarks must run serially.
- Neptune remains out of scope until reliable local testability exists.
- Self-improve candidates must not edit benchmark harness, parser, or baseline evidence.

## Acceptance

- `:graph-benchmark:compileKotlin` passes.
- JMH JSON can be normalized into JSON and Markdown.
- README/README.ko document the benchmark tracks and rerun path.

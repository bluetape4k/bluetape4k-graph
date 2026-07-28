# 이슈 193 Graph benchmark program 설계

## 맥락

Issue #193 needs one benchmark program that compares graph DB backends, graph-io implementations, and later self-improve before/after optimization results.

## 결정

Use the existing `benchmark/graph-benchmark` kotlinx-benchmark module as the unified entry point. Keep the existing TinkerGraph sync/virtual-thread benchmarks and add:

- `GraphDbComparisonBenchmark` for backend comparison through `GraphOperations`.
- `GraphIoComparisonBenchmark` for format comparison using the same generated TinkerGraph dataset.
- `normalize_jmh_report.py` for stable JMH JSON normalization and baseline/candidate deltas.

## 제약

- Container-backed DB benchmarks must run serially.
- Neptune remains out of scope until reliable local testability exists.
- Self-improve candidates must not edit benchmark harness, parser, or baseline evidence.

## 인수 기준

- `:graph-benchmark:compileKotlin` passes.
- JMH JSON can be normalized into JSON and Markdown.
- README/README.ko document the benchmark tracks and rerun path.

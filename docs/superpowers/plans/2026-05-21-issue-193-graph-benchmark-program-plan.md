# Issue 193 Graph Benchmark Program Plan

## Steps

1. Extend `benchmark/graph-benchmark` dependencies to cover graph DB backends and graph-io modules.
2. Add graph DB comparison benchmarks using `GraphOperations`.
3. Add graph-io comparison benchmarks using CSV, Jackson2, Jackson3, GraphML, and OkIO paths.
4. Add JMH report normalization for before/after comparison.
5. Add local self-improve settings and sealed-file validator.
6. Verify compile and parser behavior.
7. Record lessons and link work to Epic #193 and sub issues #188-#192.

## Verification

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin --no-build-cache`
- `python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py benchmark/graph-benchmark/src/test/resources/jmh/sample-main.json --markdown /tmp/graph-benchmark-sample.md`
- `scripts/validate-graph-benchmark-sealed.sh HEAD`

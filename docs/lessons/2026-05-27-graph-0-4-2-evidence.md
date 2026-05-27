# Graph 0.4.2 Evidence Bundle

## Context

Milestone 0.4.2 grouped follow-up issues for benchmark contract visibility and graph-io failure accounting:
`#231`, `#236`, `#237`, `#238`, and `#239`.

## Decision

Add lightweight, deterministic tests instead of expanding long-running integration coverage:

- benchmark wrappers can skip the expensive Gradle run and consume fixture reports for one-line JSON stdout contract tests.
- the SVG renderer is tested against a temp working directory so it does not write generated artifacts into the repo.
- graph-io benchmark has a `smoke` kotlinx-benchmark configuration with `sizeName=smoke`.
- GraphML import now records unsupported constructs and turns `FAIL` policy errors into a failed report before creating elements.
- CSV and GraphML skipped-record paths assert failure severity, phase, role, counts, and messages.

## Outcome

The bundle closes the visibility gap without making the normal test suite run full benchmarks.
The actual smoke benchmark remains available as an explicit Gradle task for release or CI evidence.

## Verification

- `./gradlew :graph-benchmark:test --tests '*BenchmarkScriptContractTest' --tests '*BenchmarkSvgRendererTest' :graph-io-benchmark:test --tests '*GraphIoBenchmarkSmokeTest' :bluetape4k-graph-io-csv:test --tests '*CsvImportErrorTest' --tests '*SuspendCsvImportErrorTest' :bluetape4k-graph-io-graphml:test --tests '*StaxGraphMlReaderWriterTest' --tests '*GraphMlRoundTripTest'`
- `./gradlew :graph-io-benchmark:smokeBenchmark`
- `git diff --check`

## Future Guidance

Keep benchmark CI evidence split from real performance claims. Use fixture tests for wrapper contracts, explicit smoke tasks for wiring, and full benchmark runs only when the result is intended to be interpreted.
When touching graph-io suspend tests, re-scan sibling tests for real file IO still using `runTest` and migrate touched coverage to `runSuspendIO`.
When documenting GraphML support, avoid "full compatibility" wording unless unsupported constructs are actually implemented.

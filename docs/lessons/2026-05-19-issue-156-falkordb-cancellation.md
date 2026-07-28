# 이슈 156 FalkorDB Cancellation Propagation

## 맥락

`FalkorDBGraphSuspendOperations.graphExists()` used `runCatching {}` inside a suspend function and converted every failure into `false`.

## 결정

- Rethrow `CancellationException` before ordinary exception fallback.
- Preserve the existing warn-and-return-false behavior for non-cancellation driver failures.
- Keep MockK collaborators as class-level fields and reset them in `@BeforeEach` with `clearMocks(...)`.

## 결과

`graphExists()` now preserves structured concurrency cancellation semantics, and the regression test fixes the behavior with a mocked driver that throws `CancellationException`.

## 검증

- `./gradlew :bluetape4k-graph-falkordb:compileKotlin :bluetape4k-graph-falkordb:compileTestKotlin :bluetape4k-graph-falkordb:test --tests "io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperationsTest" :bluetape4k-graph-falkordb:detekt --console=plain --no-daemon`
- `./gradlew :bluetape4k-graph-falkordb:test :bluetape4k-graph-falkordb:detekt --console=plain --no-daemon`
- `codex review --uncommitted` reported no correctness issues.
- Claude CLI advisor review reported `NO P0/P1 FINDINGS`.

## 향후 지침

Do not use `runCatching {}` around suspend-facing code unless cancellation is rethrown before fallback handling.

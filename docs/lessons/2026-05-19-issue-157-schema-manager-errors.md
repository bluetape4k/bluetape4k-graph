# 이슈 157 Schema Manager Error Handling

## 맥락

FalkorDB and Memgraph schema managers used `runCatching {}` in private DDL ignore helpers. That made cancellation and ordinary database errors flow through the same broad fallback path.

## 결정

- Replace the helper-level `runCatching {}` calls with explicit `try/catch`.
- Rethrow `CancellationException` before already-exists or missing-resource message fallbacks.
- Keep MockK driver collaborators as class-level fields and reset them in `@BeforeEach` with `clearMocks(...)`.
- Suppress detekt `TooGenericExceptionCaught` only on the two helper functions per backend because the helpers intentionally inspect expected driver exception messages.

## 결과

`createIndex()` and `dropIndex()` in both schema managers now preserve coroutine cancellation while keeping idempotent DDL behavior for expected already-exists and missing-resource responses.

## 검증

- `./gradlew :bluetape4k-graph-falkordb:compileKotlin :bluetape4k-graph-falkordb:compileTestKotlin :bluetape4k-graph-falkordb:test --tests "io.bluetape4k.graph.falkordb.FalkorDBGraphSchemaManagerTest" :bluetape4k-graph-falkordb:detekt :bluetape4k-graph-memgraph:compileKotlin :bluetape4k-graph-memgraph:compileTestKotlin :bluetape4k-graph-memgraph:test --tests "io.bluetape4k.graph.memgraph.MemgraphGraphSchemaManagerTest" :bluetape4k-graph-memgraph:detekt --console=plain --no-daemon`
- `./gradlew :bluetape4k-graph-falkordb:test :bluetape4k-graph-memgraph:test :bluetape4k-graph-falkordb:detekt :bluetape4k-graph-memgraph:detekt --console=plain --no-daemon`
- IntelliJ diagnostics could not run because the worktree was not an open IntelliJ project; Gradle compile/test/detekt was used as fallback evidence.
- Codex review found the first regression messages too weak; tests were updated to use fallback-matching cancellation messages and the follow-up review reported no blocking correctness bugs.
- Claude CLI review reported `NO P0/P1 FINDINGS`.

## 향후 지침

Do not use `runCatching {}` for cancellation-sensitive fallback helpers. Make cancellation a first branch, then handle expected driver failures explicitly.

# Batch Insert Implementation Plan

## Related Spec

- Spec: `docs/superpowers/specs/2026-05-10-batch-insert-design.md`
- Issue: #33
- Branch/worktree: `feat/33-batch-insert` in `.worktrees/feat/33-batch-insert`

## Execution Strategy

Proceed sequentially. Do not start backend-wide implementation until the core API and validation compile and are tested.
The highest-risk backend assumptions are FalkorDB map assignment and AGE literal batch shape; spike those before broad
backend edits.

## Task List

| Task | Complexity | Modules / Files | Work | Verification |
|------|------------|-----------------|------|--------------|
| T0 | low | branch/worktree, build files | Confirm worktree, issue scope, AGENTS rules, docs paths, and reconcile Java toolchain notes against actual Gradle config | `git status --short --branch`, inspect Gradle toolchain |
| T1 | high | `graph-core/src/main/kotlin/io/bluetape4k/graph/model`, `graph-core/src/main/kotlin/io/bluetape4k/graph/repository` | Add `BatchEdge`, default sync/suspend batch methods, virtual-thread async methods, and shared validation with Korean KDoc; prove `GraphOperations`, `GraphSuspendOperations`, and virtual-thread types resolve `ops.createVertices(...)` / `ops.createEdges(...)` | `./gradlew :graph-core:compileKotlin :graph-core:compileTestKotlin --no-daemon`, backend `compileTestKotlin` sweep, IDE diagnostics if available or targeted compile fallback |
| T2 | high | `graph-core/src/test` | Add default-method, validation, empty-input, size-1, facade-resolution, and virtual-thread adapter tests | `./gradlew :graph-core:test --tests '*Batch*' --no-daemon` |
| T3 | high | `graph-neo4j` | Implement native `UNWIND` batch vertex/edge create in sync operations; suspend delegates to sync native path or uses equivalent reactive query; caching wrapper invalidation | `./gradlew :graph-neo4j:test --tests '*Batch*' --no-daemon`, IDE diagnostics if available or compile fallback |
| T4 | high | `graph-memgraph` | Implement Memgraph batch create using Neo4j-compatible Cypher and verify in Testcontainer; caching wrapper invalidation | `./gradlew :graph-memgraph:test --tests '*Batch*' --no-daemon`, IDE diagnostics if available or compile fallback |
| T5 | high | `graph-falkordb` | Spike `UNWIND` + map `SET +=` support, then implement native batch create; if unsupported, implement and test property-key-set grouping; confirm no caching wrapper exists | `./gradlew :graph-falkordb:test --tests '*Batch*' --no-daemon`, IDE diagnostics if available or compile fallback |
| T6a | high | `graph-age/src/main/kotlin/.../sql/AgePropertySerializer.kt`, `graph-age/src/test/.../AgeSqlTest.kt` | Harden and lock AGE literal value serialization for quotes, backslashes, nulls, lists, nested maps, booleans, and numbers before batch SQL is added | `./gradlew :graph-age:test --tests '*AgeSql*' --no-daemon` |
| T6 | high | `graph-age`, `graph-age/src/main/kotlin/.../sql/AgeSql.kt`, `CachingAgeGraphOperations`, AGE benchmark cache wrapper | Spike AGE `UNWIND` literal row list and `SET +=`; implement chunked literal batch SQL inside one Exposed transaction; add cache invalidation and forced multi-chunk rollback test | `./gradlew :graph-age:test --tests '*Batch*' --no-daemon`, IDE diagnostics if available or compile fallback |
| T7 | high | `graph-tinkerpop` | Implement order-preserving batch create with endpoint precheck, `reentrantLock` for new coordination, and snapshot rollback | `./gradlew :graph-tinkerpop:test --tests '*Batch*' --no-daemon`, IDE diagnostics if available or compile fallback |
| T8 | high | backend test suites | Add shared behavioral tests where feasible: order, empty input, size-1 batch path, mixed property keys, distinct rows, missing endpoints, suspend variants | Targeted backend batch test commands |
| T9 | high | `graph-io/csv`, `graph-io/jackson2`, `graph-io/jackson3`, `graph-io/graphml` | Replace tight create loops with `batchSize`-driven buffers by label for sync and suspend importers; verify existing buffered I/O behavior is unchanged | `./gradlew :graph-io-csv:test :graph-io-jackson2:test :graph-io-jackson3:test :graph-io-graphml:test --no-daemon`, IDE diagnostics if available or compile fallback |
| T10 | medium | `graph-io/core/src/test`, format tests | Add tests proving import counts and failure policies stay stable when batch flushing is used | Targeted graph-io tests with `--tests '*Import*' --tests '*Batch*'` |
| T11 | high | `benchmark/graph-benchmark`, `benchmark/graph-neo4j-benchmark`, `benchmark/graph-age-benchmark`, `docs/testlogs/2026-05.md` | Add and execute 10k vertex and 10k edge benchmark/smoke evidence for Neo4j and AGE; record numeric loop baseline vs batch results | Compile benchmark modules; executed command output summarized in testlog |
| T12 | medium | `README.md`, `README.ko.md`, backend READMEs if needed, graph-io READMEs | Document public batch API, order/failure semantics, and importer `batchSize` behavior | README review + compile unaffected |
| T13 | low | `docs/superpowers/index/2026-05.md`, `docs/superpowers/INDEX.md`, `docs/testlogs/2026-05.md` if tests run | Add spec/plan index row, update counts, and record test evidence when implementation runs | File review, `git diff --check` |
| T14 | high | changed modules | Run final compile/test/diff checks, Spring Boot starter/examples compile checks, and 6-R review before PR | `./gradlew :graph-core:test :graph-tinkerpop:test --no-daemon`, backend targeted tests, `./gradlew :graph-spring-boot4-starter:compileTestKotlin :code-graph-examples:compileTestKotlin :linkedin-graph-examples:compileTestKotlin --no-daemon`, `git diff --check` |

## Ordering Constraints

- T1 and T2 must land before backend work.
- T5 and T6a/T6 begin with focused spike tests because FalkorDB and AGE have the most uncertain query shapes.
- T6a must pass before T6 batch SQL implementation starts.
- T9 must wait until at least T1 and one backend/native path are working, so importer tests can distinguish API wiring from backend failures.
- T11 should wait until backend APIs are stable enough that benchmark code will not churn.
- T12 and T13 happen after the final public API shape is stable.

## Current-Code Dependencies to Recheck Before Implementation

- Confirm Kotlin interface default methods compile with the repository's Kotlin 2.3 settings and Java 21 toolchain.
- Confirm `GraphOperations` and `GraphSuspendOperations` inherit the new methods without extra facade edits.
- Recheck `GraphVirtualThreadOperations` composition before adding async batch methods.
- Recheck caching wrapper classes in Neo4j, Memgraph, and AGE because merge/upsert already added invalidation patterns.
- Recheck graph-io module names in `settings.gradle.kts`; use actual Gradle paths for targeted tests.
- Recheck FalkorDB `jfalkordb` parameter handling with a small integration test before writing the full implementation.
- Recheck AGE SQL string limits and `AgePropertySerializer` escaping before selecting chunk size.
- New TinkerGraph batch coordination uses `reentrantLock`; existing `synchronized` sections are not refactored in this slice
  unless they directly block the batch implementation.

## Backend Implementation Notes

### Neo4j / Memgraph

- Use rows shaped as `{index, properties}` for vertices and `{index, fromId, toId, properties}` for edges.
- Always `ORDER BY index` in the return clause.
- Use one query for a chunk. For edge batch, count matched endpoints before `CREATE`.
- Throw `GraphQueryException` if returned row count differs from input size.
- Keep property maps as maps; validate keys before query construction.

### FalkorDB

- First add a focused test for:
  - `UNWIND $rows AS row CREATE (n:Person) SET n += row.properties RETURN n`
  - `UNWIND $rows AS row MATCH ... CREATE ... SET r += row.properties RETURN r`
- If map assignment fails, group rows by identical property-key set and generate per-key assignments.
- Keep `id(...) = toInteger(row.fromId)` endpoint lookup.

### AGE

- Add `AgeSql.createVerticesBatch(...)` and `AgeSql.createEdgesBatch(...)` helpers only after a focused `AgeSqlTest` proves the expected SQL shape.
- Prefer literal row-list `UNWIND`, chunked to avoid very large SQL.
- Run all chunks inside one Exposed transaction.
- Endpoint mismatch should prevent the chunk from creating edges and surface as `GraphQueryException`.
- Before implementation, extend or verify `AgePropertySerializer` tests for quotes, backslashes, nulls, lists, nested maps,
  and numeric/boolean values. Do not interpolate raw property values.
- Add a forced failure across multiple chunks to prove Exposed transaction rollback covers prior AGE `cypher()` emissions.

### TinkerGraph

- Validate all inputs first.
- For edge batch, resolve every endpoint before any create.
- Use existing snapshot/restore transaction pattern for rollback.
- Use `reentrantLock` for new batch write coordination rather than adding new `synchronized` blocks.

## Graph-IO Migration Notes

- Introduce a small internal buffer helper only if it reduces duplicated label grouping across CSV/NDJSON/GraphML importers.
- Preserve current `GraphImportReport` counts and statuses.
- Do not change `GraphImportOptions` public fields in this slice; reuse `batchSize`.
- Be careful with duplicate vertex policy: call `putFirstOrFail` before adding a vertex to the batch, then call `put` after matching returned vertices by order.
- For `SKIP_EDGE`, do not include unresolved edges in the batch.

## Verification Matrix

| Claim | Verification |
|-------|--------------|
| Core API compiles and defaults work | `./gradlew :graph-core:test --tests '*Batch*' --no-daemon` |
| Neo4j native batch works | `./gradlew :graph-neo4j:test --tests '*Batch*' --no-daemon` |
| Memgraph native batch works | `./gradlew :graph-memgraph:test --tests '*Batch*' --no-daemon` |
| FalkorDB query assumptions hold | `./gradlew :graph-falkordb:test --tests '*Batch*' --no-daemon` |
| AGE query assumptions hold | `./gradlew :graph-age:test --tests '*Batch*' --no-daemon` |
| TinkerGraph rollback/order works | `./gradlew :graph-tinkerpop:test --tests '*Batch*' --no-daemon` |
| graph-io import semantics unchanged | `./gradlew :graph-io-csv:test :graph-io-jackson2:test :graph-io-jackson3:test :graph-io-graphml:test --no-daemon` |
| Public docs compile-neutral | README review plus targeted compile/test commands |
| Spring Boot starter still compiles | `./gradlew :graph-spring-boot4-starter:compileTestKotlin --no-daemon` |
| Examples still compile | `./gradlew :code-graph-examples:compileTestKotlin :linkedin-graph-examples:compileTestKotlin --no-daemon` |
| No whitespace/path errors | `git diff --check` |

## Rollback / Re-Run Points

- After T2: if Kotlin default interface additions cause unexpected binary/source issues, switch to capability interface API before backend work.
- After T5 spike: if FalkorDB cannot support a safe native batch path, document a backend limitation and decide whether the default loop is acceptable for FalkorDB in this PR.
- After T6 spike: if AGE literal `UNWIND` is not viable, fall back to chunked `CREATE` lists inside one Exposed transaction and record the perf limitation.
- After T9: if graph-io batching changes partial failure behavior, revert importer migration and keep batch API limited to core/backends for the first PR.
- Before T14: if container-heavy tests fail from environment issues, rerun the failing module once and classify with raw evidence before changing code.

## Docs / README / AGENTS Impact

- README.md and README.ko.md need a short batch insert API section.
- Backend READMEs may need notes if a backend has a batch limitation.
- `graph-io` README files should mention that `GraphImportOptions.batchSize` now controls write flushing.
- No AGENTS.md update is expected unless implementation discovers a durable new convention.
- `docs/superpowers/index/2026-05.md` and `docs/superpowers/INDEX.md` must be updated with this spec/plan row.

## Migration Scope

This PR should include:

- Core sync/suspend/virtual-thread batch API.
- Production overrides for Neo4j, Memgraph, FalkorDB, AGE, and TinkerGraph.
- Sync and suspend CSV importers.
- Sync and suspend Jackson2/Jackson3 NDJSON importers.
- Sync and suspend GraphML importers.
- Virtual-thread importer adapters only where they directly wrap the migrated sync path.

This PR should not include:

- New example modules.
- Existing code/linkedin example rewrites beyond small README samples.
- A new streaming output API.

## Step 3-P Risk Mitigations

- Security: central label/property-key validation before query construction.
- Reliability: edge endpoint precheck/count check before create; TinkerGraph rollback.
- Performance: native one-query/chunked writes for Cypher backends; graph-io batch flushing.
- Compatibility: default repository methods avoid source-breaking abstract additions.
- Coroutine correctness: suspend APIs either use native reactive query or explicit `Dispatchers.IO` delegation matching existing backend style.

## Step 3-R Review Notes

### Local Perspective Reviews

| Perspective | Finding | Severity | Plan Decision |
|-------------|---------|----------|---------------|
| Implementer | Backend work depends on core defaults and validation. | high | T1/T2 are first and block backend tasks. |
| Test engineer | Counting rows is not enough; order and missing endpoint rollback must be asserted. | high | T8 includes order and all-or-fail tests. |
| Architect | graph-io migration can hide API bugs if started too early. | medium | T9 waits until core and at least one backend path work. |
| Delivery | Container-heavy backends may slow one PR. | medium | Targeted per-backend tests and rollback points are explicit. |
| Ops/SRE | Partial graph state during import is the main operational risk. | high | Edge batch and importer failure semantics are dedicated checks. |

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/ask-claude-batch-insert-plan-20260510-135723.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Severity | Finding | Decision | Follow-up |
|----------|---------|----------|-----------|
| high | 10k benchmark evidence cannot be optional. | accepted | Promoted T11 to high and requires executed numeric evidence in testlog. |
| high | AGE serializer hardening must be a separate precondition. | accepted | Added T6a before AGE batch SQL. |
| high | Cache invalidation was incomplete for AGE and cache presence should be confirmed for FalkorDB. | accepted | Added AGE cache work and FalkorDB absence confirmation. |
| high | Facade-resolution and suspend default ABI need broader compile gates. | accepted | Expanded T1/T2 verification and backend compile sweep. |
| medium | Diagnostics, Spring Boot starter, examples, size-1 behavior, and rollback tests were under-specified. | accepted | Added diagnostic fallback wording, compile checks, size-1 tests, and AGE rollback test. |

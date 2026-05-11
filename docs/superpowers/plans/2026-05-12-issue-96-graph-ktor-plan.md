# Issue #96 graph-ktor Implementation Plan

> Spec: [2026-05-12-issue-96-graph-ktor-design.md](../specs/2026-05-12-issue-96-graph-ktor-design.md)  
> Related issue: [#96](https://github.com/bluetape4k/bluetape4k-graph/issues/96)

## Plan Summary

`graph-ktor`를 Ktor 3.x custom plugin module로 추가한다. Core plugin state는 `GraphOperations`와
`GraphSuspendOperations`를 저장하고, backend-specific helper는 optional dependency boundary를 유지하도록 별도 file에 둔다.

## Tasks

| Task | Complexity | Scope | Verification |
|---|---:|---|---|
| T1. Gradle/catalog/module registration | medium | `settings.gradle.kts`, `gradle/libs.versions.toml`, `ktor/graph-ktor/build.gradle.kts`, example build | `./gradlew projects`, `:graph-ktor:compileKotlin` |
| T2. Core Ktor plugin/state/API | high | `GraphPlugin`, `GraphPluginConfig`, `GraphPluginState`, `ApplicationExt` | `:graph-ktor:compileKotlin`, plugin tests |
| T3. Backend helper functions | medium | TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB config extension files | `:graph-ktor:compileKotlin`, `:graph-ktor:compileTestKotlin` |
| T4. Ktor tests | medium | `testApplication` install/access/failure/lifecycle tests | `:graph-ktor:test` |
| T5. Example module | medium | `examples/ktor-graph-examples` app/routes/tests | `:ktor-graph-examples:test` |
| T6. Docs/index/changelog | low | module/example/root/BOM README pairs, CHANGELOG, docs index | `git diff --check`, link/path spot check |
| T7. Review/cleanup/verification | high | Step 4-S/4-P/5/6/6-R gates | targeted tests + review convergence |

All Kotlin tasks apply `$bluetape4k-patterns`: Korean KDoc for public API, bluetape4k validation helpers, coroutine cancellation/lifecycle review, Ktor testApplication tests, README sync.

## Detailed Steps

### T1. Gradle/catalog/module registration

- Add `ktor = "3.4.3"` from existing `bluetape4k-leader` catalog with mvnrepository link.
- Add Ktor aliases:
  - `ktor-bom`
  - `ktor-server-core`
  - `ktor-server-cio`
  - `ktor-server-test-host`
  - `ktor-server-content-negotiation`
  - `ktor-serialization-jackson`
- Add `includeModules("ktor", false, false)` in `settings.gradle.kts`.
- Create `ktor/graph-ktor/build.gradle.kts`.
- Create `examples/ktor-graph-examples/build.gradle.kts`.
- `ktor/graph-ktor/build.gradle.kts` dependencies:
  - `api(project(":graph-core"))`
  - `api(libs.bluetape4k.coroutines)`
  - `compileOnly(libs.ktor.server.core)`
  - `compileOnly(project(":graph-tinkerpop"))`
  - `compileOnly(project(":graph-neo4j"))`
  - `compileOnly(project(":graph-memgraph"))`
  - `compileOnly(project(":graph-age"))`
  - `compileOnly(project(":graph-falkordb"))`
  - `testImplementation(libs.ktor.server.core)`
  - `testImplementation(libs.ktor.server.cio)`
  - `testImplementation(libs.ktor.server.test.host)`
  - `testImplementation(project(":graph-tinkerpop"))`
- `ktor-serialization-jackson` and `ktor-server-content-negotiation` are for `examples/ktor-graph-examples`, not for the core
  `graph-ktor` module.

Rollback point: if catalog alias naming collides with existing Gradle generated accessors, rename before code implementation.

### T2. Core Ktor plugin/state/API

- `GraphPluginConfig`
  - internal factories for `GraphOperations` and `GraphSuspendOperations`
  - close action registry
  - public `operations(...)` injection method
  - `operations(graphOperations, graphSuspendOperations, closeOnStop = false)`:
    - `closeOnStop=false`: plugin does not close caller-owned operations.
    - `closeOnStop=true`: plugin closes the two supplied objects once each by object identity.
    - If the two objects share a hidden delegate, caller must either keep `closeOnStop=false` and register its own close action later,
      or pass idempotent wrappers. Backend helpers must avoid hidden double close themselves.
- `GraphPluginState`
  - immutable state object storing resolved operations and close actions
  - `close()` closes registered actions independently with WARN logs
- `GraphPlugin`
  - `createApplicationPlugin(name = "Graph", createConfiguration = ::GraphPluginConfig)`
  - fail fast when backend/operations not configured
  - store state in `Application.attributes`
  - subscribe `ApplicationStarted` / `ApplicationStopped`
- `Application` extensions:
  - `graphPluginState()`
  - `graphOperations()`
  - `graphSuspendOperations()`
- `ApplicationCall` extensions:
  - `graphOperations()`
  - `graphSuspendOperations()`

Verification: `GraphPluginTest` covers missing backend and extension access.

### T3. Backend helper functions

Separate files:

- `TinkerGraphPluginConfig.kt`: `GraphPluginConfig.tinkerGraph()`
- `Neo4jGraphPluginConfig.kt`: `GraphPluginConfig.neo4j(driver, database = "neo4j")`
- `MemgraphGraphPluginConfig.kt`: `GraphPluginConfig.memgraph(driver, database = "memgraph")`
- `AgeGraphPluginConfig.kt`: `GraphPluginConfig.age(graphName)`
- `FalkorDBGraphPluginConfig.kt`: `GraphPluginConfig.falkorDB(driver, graphName)`

Close/lifecycle rules:

- TinkerGraph helper creates one `TinkerGraphOperations` delegate and wraps it with `TinkerGraphSuspendOperations(delegate)`.
  It registers exactly one close action for the shared delegate path. It must not register both sync and suspend wrappers.
- Neo4j/Memgraph/FalkorDB helpers never close injected drivers. Their operations currently treat drivers as caller-owned.
- AGE helper requires caller to call Exposed `Database.connect(...)` before graph operations are used. This module does not accept or
  own `Database` in the initial API because the existing AGE operations depend on Exposed's configured transaction manager.
- Memgraph default database is `memgraph`, matching `MemgraphGraphOperations` and `MemgraphGraphProperties`.

Dependency strategy:

- backend modules are `compileOnly`.
- tests use `testImplementation(project(":graph-tinkerpop"))` for runtime behavior.
- backend helper compile coverage verifies signatures and constructor use.

### T4. Ktor tests

Use Ktor `testApplication` and bluetape4k assertions.

Test cases:

- `GraphPlugin` without backend selection throws `IllegalArgumentException`.
- installed plugin exposes state, sync operations, suspend operations.
- uninstalled extension access throws `IllegalStateException`.
- `operations(...)` custom injection works with fake close actions.
- lifecycle close failure isolation: one close action throwing does not prevent later close actions.
- `ApplicationStopped` closes plugin-owned TinkerGraph operations without closing external drivers.
- TinkerGraph route smoke: install plugin and use `graphSuspendOperations()` from a Ktor route.
- route accessor smoke: use `call.graphOperations()` / `call.graphSuspendOperations()` from route handlers.

`MultithreadingTester`, `StructuredTaskScopeTester`, `SuspendedJobTester`: not used. The risk here is Ktor plugin install/lifecycle contract, not race/structured concurrency logic. Coroutine behavior is covered by `testApplication` + suspend route smoke.

### T5. Example module

- Use TinkerGraph so the example does not require Docker or external DB.
- Main app installs `GraphPlugin { tinkerGraph() }`.
- Expose simple city graph routes:
  - `GET /health`
  - `POST /demo/reset`
  - `GET /cities/count`
  - `GET /cities/path`
- Tests use `testApplication` to verify routes.

Rollback point: if Jackson/Ktor content negotiation creates dependency friction, route responses can be text-only for the initial example.

### T6. Docs/index/changelog

- `ktor/graph-ktor/README.md` and `README.ko.md`
- `examples/ktor-graph-examples/README.md` and `README.ko.md`
- root README pair module structure and dependency examples
- BOM README pair managed module list: include `graph-ktor`; continue excluding `examples/*`
- `CHANGELOG.md`
- `docs/superpowers/index/2026-05.md` and `docs/superpowers/INDEX.md` if count/update pattern requires it

### T7. Verification and review

Commands:

```bash
./gradlew projects --no-daemon
./gradlew :graph-ktor:compileKotlin :graph-ktor:compileTestKotlin --no-daemon
./gradlew :graph-ktor:test :ktor-graph-examples:test --no-daemon
git diff --check
```

Expected PR checks:

- Step 5 verifier checks spec/plan acceptance criteria against code.
- Step 6-R runs security, Ops/SRE, structural, Kotlin quality, tests/types/silent failure, performance/stability tiers.
- Claude Code Opus advisor runs for spec/plan and final code review when local CLI works.

## Step 3-R Review Notes

### Iteration 1 Integrated Findings

| Priority | Finding | Decision |
|---|---|---|
| P1 | `GraphPluginConfig.operations(...)` must avoid double close when sync/suspend share one delegate. | accepted: close actions explicit and helper-specific. |
| P1 | Tests must prove missing backend fails during install, not first request. | accepted: dedicated `testApplication` failure test. |
| P1 | Route context needs route-safe accessors. | accepted: add `ApplicationCall` extensions and route tests. |
| P1 | AGE helper must state Exposed `Database.connect(...)` precondition. | accepted: KDoc/README requirement added. |
| P1 | build.gradle dependency list must be explicit. | accepted: T1 dependency block added. |
| P2 | Backend parity can accidentally become runtime parity. | accepted: compileOnly backend modules plus README dependency instructions. |
| P2 | Example should avoid external infra. | accepted: TinkerGraph only. |
| P2 | Lifecycle failure isolation should be tested. | accepted: T4 test added. |

Convergence: P0 = 0, P1 = 0.

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-issue-96-graph-ktor-spec-plan-20260512-032751.md`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | route context API sketch mismatch | accepted | `ApplicationCall` accessors added |
| P1 | TinkerGraph double close policy missing | accepted | helper close rule specified |
| P1 | AGE `Database.connect(...)` contract missing | accepted | helper KDoc/docs requirement added |
| P1 | Gradle dependency plan too vague | accepted | explicit dependency list added |
| P1 | `closeOnStop` semantics undefined | accepted | close semantics defined |
| P2 | lifecycle close failure test missing | accepted | T4 updated |

## Step 3 Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Plan path confirmed inside feature worktree | Done | this file |
| All tasks have complexity labels | Done | Tasks table |
| `$bluetape4k-patterns` applied to code tasks | Done | Plan summary and tasks |
| Plan snippets conform to patterns | Done | no unsafe code snippets |
| Thread/coroutine helper decision recorded | Done | T4 |
| Tests and verification tasks included | Done | T4/T7 |
| README/README.ko tasks included | Done | T6 |
| Risky ordering/dependency assumptions explicit | Done | T1/T3 rollback/dependency notes |
| Spec + plan committed before implementation | Pending | to commit after Step 3-R advisor |

## Step 3-R Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Multi-perspective plan review complete | Done | implementer/test/architecture/delivery integrated locally |
| Claude Code Opus advisor review complete or gap recorded | Done | local Claude CLI completed |
| Claude advisor artifact path recorded | Done | `.omx/artifacts/claude-issue-96-graph-ktor-spec-plan-20260512-032751.md` |
| Plan review complete | Done | review notes above |
| Spec criteria map to tasks/commands | Done | acceptance criteria covered by T1-T7 |
| Task ordering implementable | Done | Gradle -> core -> helpers -> tests -> docs |
| Findings normalized | Done | review table |
| P0 revised/re-reviewed | N/A | no P0 |
| P1 revised/re-reviewed | Done | T2/T4 close and fail-fast tasks |
| Convergence verification passed | Done | P0 = 0, P1 = 0 |
| Closure declared only after P0/P1 zero | Done | review notes |
| P2/P3 recorded | Done | review table |
| Open questions surfaced | Done | no remaining material question |

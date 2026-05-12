# Issue #96 graph-ktor Design

> Language: Korean with English technical terms.
> Related issue: [#96](https://github.com/bluetape4k/bluetape4k-graph/issues/96)

## 1. 문제와 목표

`bluetape4k-graph`는 `graph-spring-boot`로 Spring Boot 4 application에서 `GraphOperations`와
`GraphSuspendOperations`를 자동 등록할 수 있다. 하지만 Ktor application은 Spring container가 없고,
Ktor idiom은 `install(...)` 기반의 explicit plugin model이다.

목표는 `ktor/graph-ktor` module과 `examples/ktor-graph-examples`를 추가해 Ktor 3.x application에서
graph backend를 명시적으로 선택하고, `Application` extension으로 graph facade에 접근하게 만드는 것이다.

## 2. 현재 근거

- Issue #96은 `ktor/graph-ktor`, `examples/ktor-graph-examples` 추가를 요구한다.
- `bluetape4k-leader/leader-ktor`는 `createApplicationPlugin(...)`, `Application.attributes`,
  `testApplication` 검증을 이미 사용한다.
- Ktor 공식 documentation은 custom plugin에 `createApplicationPlugin(name, createConfiguration = ...)`를 사용하고,
  lifecycle event는 `MonitoringEvent(ApplicationStarted/Stopped)`로 구독하는 패턴을 제시한다.
- `graph-spring-boot`는 backend scope로 TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB를 다룬다.
- Backend constructors:
  - `TinkerGraphOperations()` + `TinkerGraphSuspendOperations(delegate)`
  - `Neo4jGraphOperations(driver, database)` + `Neo4jGraphSuspendOperations(driver, database)`
  - `MemgraphGraphOperations(driver, database)` + `MemgraphGraphSuspendOperations(driver, database)`
  - `AgeGraphOperations(graphName)` + `AgeGraphSuspendOperations(graphName)`
  - `FalkorDBGraphOperations(driver, graphName)` + `FalkorDBGraphSuspendOperations(driver, graphName)`

## 3. 범위

### 포함

- `ktor/graph-ktor` Gradle module 추가
- Ktor `GraphPlugin` + `GraphPluginConfig`
- `Application.graphPluginState()`, `Application.graphOperations()`, `Application.graphSuspendOperations()`
- Backend selection helper functions:
  - `GraphPluginConfig.operations(...)`
  - `GraphPluginConfig.tinkerGraph()`
  - `GraphPluginConfig.neo4j(driver, database)`
  - `GraphPluginConfig.memgraph(driver, database)`
  - `GraphPluginConfig.age(graphName)`
  - `GraphPluginConfig.falkorDB(driver, graphName)`
- `examples/ktor-graph-examples` TinkerGraph-based executable/testable usage
- `README.md` / `README.ko.md` for module and example
- Root README, BOM README, CHANGELOG, docs index updates

### 제외

- Spring Boot integration rename. 이 작업은 issue #99에서 별도 처리한다.
- `GraphVirtualThreadOperations` exposure. Ktor integration의 initial scope는 coroutine-first `GraphSuspendOperations`와
  compatibility용 `GraphOperations`이다.
- Ktor plugin에서 DataSource, Neo4j Driver, FalkorDB Driver를 직접 생성하는 property DSL. Ktor는 DI/container가
  선택지가 다양하므로 initial version은 caller-owned resource injection을 기준으로 한다.

## 4. Design Options

### Option A: Core injection only

`GraphPluginConfig.operations(graphOperations, graphSuspendOperations)`만 제공한다.

- 장점: dependency surface가 가장 작고 backend class loading risk가 낮다.
- 단점: issue의 backend parity 요구를 API 수준에서 만족하지 못하고 caller boilerplate가 크다.

### Option B: Core injection + backend extension helpers

Core plugin은 `graph-core`만 직접 의존하고, backend별 helper는 별도 source file의 extension function으로 둔다.
Backend modules는 `compileOnly`로 참조하고 test에서는 필요한 backend를 `testImplementation`으로 검증한다.

- 장점: plugin core는 backend에 강결합되지 않고, Spring Boot starter와 backend parity를 API helper 수준에서 맞춘다.
- 장점: 사용자는 실제 backend module을 명시적으로 추가해야 하므로 implicit runtime fallback을 피한다.
- 단점: helper function을 쓰려면 user build에 해당 backend dependency가 필요하다.

### Option C: graph-ktor가 모든 backend를 runtime dependency로 포함

`graph-ktor`가 TinkerGraph/Neo4j/Memgraph/AGE/FalkorDB를 모두 `api`/`implementation`으로 포함한다.

- 장점: user setup이 쉽다.
- 단점: Ktor plugin 하나만 추가해도 DB drivers와 Exposed/PostgreSQL/FalkorDB/TinkerPop이 모두 따라와 runtime surface가 과하다.
- 단점: explicit backend selection이라는 issue 의도와 Maven dependency hygiene에 맞지 않는다.

## 5. 선택

Option B를 채택한다.

`GraphPlugin` core는 `GraphOperations`와 `GraphSuspendOperations` pair를 해결해 `Application.attributes`에 저장한다.
Backend parity는 backend-specific extension functions로 제공한다. 이 구조는 `leader-ktor`의 plugin/state 패턴을 따르면서도
Ktor application의 explicit installation model에 맞다.

## 6. API Sketch

```kotlin
fun Application.module(driver: Driver) {
    install(GraphPlugin) {
        neo4j(driver, database = "neo4j")
    }

    val graph = graphOperations()
    val suspendGraph = graphSuspendOperations()
}
```

```kotlin
fun Application.module() {
    install(GraphPlugin) {
        tinkerGraph()
    }

    routing {
        get("/cities/count") {
            call.respondText(call.graphOperations().countVertices("City").toString())
        }
    }
}
```

Route handler에서 자연스럽게 사용할 수 있도록 `ApplicationCall.graphOperations()`와
`ApplicationCall.graphSuspendOperations()`도 제공한다. `Application` receiver가 명확한 application setup code에서는
`graphOperations()`를 그대로 사용하고, route context에서는 `call.graphOperations()`를 사용한다.

## 7. Failure Paths

- Plugin 미설치 상태에서 `graphPluginState()`, `graphOperations()`, `graphSuspendOperations()` 호출:
  `IllegalStateException` with clear message.
- `install(GraphPlugin)`에서 backend를 선택하지 않음:
  `IllegalArgumentException` with clear message.
- `operations(...)`에 blank name 등 별도 validation이 필요한 값은 backend constructor의 validation에 위임하거나
  `requireNotBlank`으로 plugin helper에서 fail fast 한다.

## 8. Lifecycle

- Caller-owned resources are not closed by default.
- `GraphPluginConfig.operations(..., closeOnStop = true)` and backend helpers can register close actions when the plugin owns
  operations created inside the helper.
- `ApplicationStopped`에서 registered close actions를 개별 `runCatching`으로 닫고, 실패는 WARN log로 기록한다.
- `TinkerGraph` helper는 plugin이 `TinkerGraphOperations`를 직접 생성하므로 stop 시 close한다.
- Neo4j/Memgraph/FalkorDB helper는 injected driver를 caller-owned로 보고 driver를 닫지 않는다. 생성된 operations만 close한다.
- AGE helper는 Exposed `Database` lifecycle을 소유하지 않으므로 operations만 close한다. 호출 전에 caller가
  `Database.connect(...)`를 완료해야 하며, 이 조건을 KDoc/README에 명시한다.

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Backend helper public signatures가 optional dependency를 class loading에 끌어들임 | `graph-ktor` only user가 `NoClassDefFoundError`를 볼 수 있음 | backend helper를 separate files로 분리하고 compile/test로 core-only access를 검증 |
| `GraphOperations.close()`와 `GraphSuspendOperations.close()`가 같은 delegate를 두 번 닫음 | TinkerGraph helper에서 double close 가능 | close actions를 중복 등록하지 않거나 tolerant close로 처리 |
| Ktor lifecycle cleanup이 누락됨 | in-memory graph/resource leak | `ApplicationStopped` test로 close action 호출 검증 |
| Testcontainers backend 검증이 무거워짐 | PR feedback 느림 | plugin API unit tests는 TinkerGraph + fake operations 중심으로 유지하되, 기존 singleton Testcontainers가 있는 Neo4j/Memgraph/AGE/FalkorDB helper는 route-level smoke로 작게 검증 |
| Example이 production infra를 요구함 | 사용자가 바로 실행하기 어려움 | example은 TinkerGraph in-memory로 구성 |

## 10. Acceptance Criteria

| Item | Status target |
|---|---|
| `ktor/graph-ktor` module 추가 | Done |
| `GraphPlugin` install 가능 | Done |
| backend explicit selection 요구 | Done |
| Spring Boot starter와 backend scope parity helper 제공 | Done |
| `GraphOperations` / `GraphSuspendOperations` Application extension 제공 | Done |
| plugin 미설치/config 누락/backend 미선택 failure path 검증 | Done |
| Ktor `testApplication` tests 추가 | Done |
| `examples/ktor-graph-examples` usage 추가 | Done |
| README.md / README.ko.md sync | Done |
| BOM/root module list 갱신 | Done |

## 11. Step 2-R Review Notes

### Iteration 1 Integrated Findings

| Priority | Finding | Decision |
|---|---|---|
| P1 | Backend helper가 optional dependency class loading risk를 만든다. | accepted: helper를 backend별 source file extension으로 분리한다. |
| P1 | Resource lifecycle contract가 불명확하면 caller-owned driver를 닫을 수 있다. | accepted: injected driver는 닫지 않고 operations close만 수행한다. |
| P1 | Route handler 예시가 `Application` extension을 route context에서 직접 호출한다. | accepted: `ApplicationCall` extension을 추가하고 예시를 `call.graphOperations()`로 수정한다. |
| P1 | TinkerGraph helper의 double close 정책이 구체적이지 않다. | accepted: helper는 `TinkerGraphOperations` delegate를 하나 만들고 close action을 1회만 등록한다. |
| P1 | AGE helper의 Exposed `Database.connect(...)` 사전 조건이 문서화되지 않았다. | accepted: lifecycle/KDoc/README에 caller 책임을 명시한다. |
| P2 | Full Testcontainers backend plugin tests는 무겁다. | revised: exhaustive backend behavior는 각 backend module에 남기고, `graph-ktor`는 기존 Testcontainers launcher로 route-level smoke만 추가한다. |
| P3 | Driver-creating DSL은 편리하지만 scope expansion이다. | deferred: follow-up에서 필요 시 다룬다. |

Convergence: P0 = 0, P1 = 0.

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-issue-96-graph-ktor-spec-plan-20260512-032751.md`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Route context API sketch compile risk | accepted | `ApplicationCall` extensions added to design/plan |
| P1 | TinkerGraph double close policy missing | accepted | single close action rule recorded |
| P1 | AGE `Database.connect(...)` precondition missing | accepted | lifecycle/docs requirement added |
| P1 | `graph-ktor` backend `compileOnly` dependencies not explicit | accepted | plan T1 updated |
| P1 | `operations(..., closeOnStop = true)` semantics unclear | accepted | plan T2 defines close semantics |
| P2 | lifecycle failure isolation test missing | accepted | plan T4 updated |

## 12. Step Checklist Completion Report

### Step 0 Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Feature worktree created or explicit reason recorded | Done | `.worktrees/feat-issue-96-graph-ktor` |
| Subsequent commands run inside the worktree | Done | `pwd` confirmed |
| Spec/plan written inside worktree | Done | this file |
| Worktree refreshed from current `origin/develop` | Done | branch ahead 0, behind 0 at start |

### Step 1 Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Target repository confirmed | Done | `bluetape4k/bluetape4k-graph` |
| Memory anchors searched | Done | graph-io and graph-server memories checked |
| Review-only boundary recorded | N/A | user requested implementation + PR |
| Concrete artifact inspected | Done | issue #96 |
| User intent and boundaries clear | Done | #96 plus follow-up: Spring Boot rename separate (#99) |
| Ambiguous requirements clarified | Done | `graph-ktor` remains, Spring Boot rename separated |

### Step 1-R Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Official docs checked | Done | Ktor custom plugin / lifecycle / testApplication docs via Context7 |
| Current repo and ecosystem reuse searched | Done | `leader-ktor`, Spring Boot integration module, backend constructors |
| Third-party API assumptions checked | Done | Ktor docs and current dependency catalog reference |
| Adopt/borrow/skip decisions recorded | Done | Option B |
| Technical constraints identified | Done | Kotlin 2.3, Java 21, Ktor 3.4.3, backend dependency boundary |
| Research summary ready | Done | sections 2-9 |

### Step 2 Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Architecture pre-design ran or skip reason recorded | Done | Options A/B/C compared |
| Step 1-R research incorporated | Done | section 2 |
| Current behavior claims cite evidence | Done | current source paths summarized |
| Spec path confirmed inside feature worktree | Done | this file |
| Risks/failure modes included | Done | section 9 |
| Approach comparison and rejection rationale included | Done | section 4 |
| Brainstorming process ran or skip reason recorded | Done | three options compared |
| User approval obtained per material section | Done | issue #96 and follow-up confirmations define boundary |
| Spec examples conform to patterns | Done | no blocking/suspend anti-pattern |
| Open questions resolved/escalated | Done | Spring Boot rename separated as #99 |
| Draft task list returned | Done | plan file |

### Step 2-R Checklist Completion Report

| Item | Status | Notes |
|---|---|---|
| Four perspective reviews complete | Done | developer/security/Ops/caller perspectives integrated locally |
| Claude Code Opus advisor review complete or gap recorded | Done | local Claude CLI completed |
| Claude advisor artifact path recorded | Done | `.omx/artifacts/claude-issue-96-graph-ktor-spec-plan-20260512-032751.md` |
| Critic integration complete | Done | section 11 |
| Findings normalized | Done | section 11 |
| P0 revised/re-reviewed | N/A | no P0 |
| P1 revised/re-reviewed | Done | design includes separate helper files and lifecycle contract |
| Convergence verification passed | Done | P0 = 0, P1 = 0 |
| Closure declared only after P0/P1 zero | Done | section 11 |
| P2/P3 recorded | Done | section 11 |
| Open questions surfaced | Done | no remaining material question |

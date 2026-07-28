# PR #100 graph-ktor 레슨

## 맥락

- PR: #100 `feat: graph-ktor Ktor plugin module 추가`
- Issue: #96
- Merge commit: `3a883f25ccea0bb3588de7775cfc360312d9abd9`
- 범위: `ktor/graph-ktor` module, `examples/ktor-graph-examples`, backend helper functions, Ktor route accessors, README/BOM/CHANGELOG/superpowers docs.
- 검증 used in PR: `./gradlew projects --no-daemon`, targeted `graph-ktor` backend smoke tests, `:graph-ktor:test`, `:ktor-graph-examples:test`, module builds, `git diff --check`, GitHub CI.

## 교훈과 학습

- 레슨: Ktor route handler에서 `Application` extension을 직접 호출하는 API sketch는 컴파일되지 않는다.
  - 증거: Claude spec/plan advisor가 route context mismatch를 P1로 지적했고, `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()`를 추가했다.
  - 향후 가드: Ktor plugin spec에는 application setup accessor와 route accessor를 분리해 적는다.

- 레슨: `GraphOperations`와 `GraphSuspendOperations`는 메서드명이 겹치므로 하나의 class가 둘을 동시에 delegation으로 구현하는 test double은 Kotlin signature conflict를 만든다.
  - 증거: `DualGraphOperations` test double compile 실패 후 제거.
  - 향후 가드: sync/suspend facade pair의 shared delegate lifecycle은 helper 내부에서 명시적으로 관리하고, external caller pair는 KDoc contract로 책임을 분리한다.

- 레슨: 신규 publishable module을 추가하면 BOM README와 root README뿐 아니라 `settings.gradle.kts` auto-include 결과를 `./gradlew projects`로 확인해야 한다.
  - 증거: `:graph-ktor`와 `:ktor-graph-examples`가 `./gradlew projects`에 각각 publishable/example module로 등록됨을 확인했다.
  - 향후 가드: module 추가 plan에 `projects`, targeted `build`, README/BOM sync를 함께 둔다.

- 레슨: backend helper를 추가할 때 compile coverage만으로 충분하다고 단정하면 기존 lightweight Testcontainers fixture를 놓칠 수 있다.
  - 증거: `FalkorDBServer`는 Redis 기반 singleton fixture라 `graph-ktor` route-level runtime smoke에 적합했고, 공통 `bluetape4k-testcontainers`의 Neo4j/Memgraph/AGE launcher도 같은 방식으로 재사용 가능했다.
  - 향후 가드: helper parity 작업에서는 먼저 backend별 existing test fixture를 찾아보고, exhaustive behavior test가 아니라 small wiring smoke로 가능한 runtime 검증을 추가한다.

## 수정 기록

- 잘못한 점: `graph-ktor` backend helper를 만들면서 "외부 DB backend는 무겁다"는 일반론을 너무 빨리 적용했다. 그 결과 `FalkorDBServer`처럼 이미 가볍고 재사용 가능한 Testcontainers fixture가 있는 backend까지 compile-level 검증으로 낮춰 판단했다.
- 중요한 이유: helper 함수는 단순 constructor wrapper처럼 보여도 Ktor plugin lifecycle, route accessor, sync/suspend facade 연결이 함께 맞아야 한다. Runtime smoke 없이 compile coverage만 두면 helper signature는 맞지만 실제 route wiring이 깨지는 문제를 놓칠 수 있다.
- 수정한 접근: backend 자체 동작의 exhaustive test는 각 backend module에 남기고, integration module인 `graph-ktor`는 backend별 helper가 Ktor route 안에서 최소한 동작하는지만 검증한다.
- 재발 방지 기준: "무거운 integration test"와 "작은 wiring smoke"를 구분한다. 기존 singleton Testcontainers launcher가 있고 테스트 시간이 합리적이면 wiring smoke를 추가한다.

## 다음 작업 체크리스트

- 먼저 existing fixture를 검색한다: `rg "Server\\.Launcher|testFixtures|Testcontainers" graph examples -g '*.kt' -g '*.kts'`.
- Test depth를 정하기 전에 backend별 runtime cost를 분류한다.
- Production dependency boundary는 `compileOnly`로 유지하고, runtime backend dependency는 `testImplementation`에만 추가한다.
- Ktor plugin helper는 실제 route를 통해 두 경로를 모두 검증한다:
  - `call.graphOperations()` for sync facade access.
  - `call.graphSuspendOperations()` for suspend facade access.
- Smoke test 이후 reusable container state를 정리한다. 특히 singleton launcher가 container reuse를 사용할 때는 반드시 정리한다.
- Spec/plan/README에 test boundary를 기록해서 "not exhaustive"가 "not runtime-tested"로 바뀌지 않게 한다.

# bounded chunk capability 정렬 Implementation Plan

> **For agentic workers:** 승인된 설계를 단계별로 실행한다. 각 단계는 실패 테스트, 최소 구현, 독립 검증 순서로 진행하며 기존 `CHUNKED_*` API의 호환성을 보존한다.

**Goal:** graph-core가 API 수준 chunking과 backend가 전체 결과를 materialize하지 않는 bounded 실행을 별도 capability로 표현하고, TinkerGraph의 실제 경로와 네 container backend의 제한, graph-io 문서를 같은 계약으로 정렬한다.

**Architecture:** 기존 `CHUNKED_READ`/`CHUNKED_EXPORT`와 repository 기본 fallback은 유지한다. `GraphBoundedChunkOperations` marker를 실제 traversal iterator를 사용하는 구현에만 적용하고, `BOUNDED_CHUNKED_READ`/`BOUNDED_CHUNKED_EXPORT`를 추가한다. capability 계산은 API marker와 bounded marker를 각각 독립적으로 투영하며, exporter 동작 자체는 변경하지 않는다.

**Tech Stack:** Kotlin 2.4.10/JVM 25, Gradle 9.7, graph-core repository interfaces, TinkerPop traversal, JUnit 5, MockK/Kluent, `bluetape4k-assertions`, Detekt.

---

## 파일 소유권과 변경 지도

| 책임 | 생성/수정 파일 | 검증 |
| --- | --- | --- |
| capability 계약 | `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphCapabilities.kt` 및 marker 파일 | core capability tests, ABI/compile |
| RED/GREEN 회귀 | `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphCapabilitiesTest.kt`, `GraphBatchOperationsTest.kt` | targeted core tests |
| bounded reference backend | `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt`, `TinkerGraphSuspendOperations.kt` | Tinker capability/conformance/chunk tests |
| conformance 계약 | `graph/graph-core/src/testFixtures/kotlin/io/bluetape4k/graph/conformance/AbstractGraphCapabilityConformanceTest.kt` 및 backend별 capability test | backend별 순차 conformance |
| capability 안내 | root `README.md`/`README.ko.md`, `graph/graph-core/README.md`/`README.ko.md`, `graph-io/graphml/README.md`/`README.ko.md`, 관련 KDoc | 문서 grep·locale audit |
| durable lesson | `docs/lessons/2026-08-25-issue-536-bounded-chunk-capability.md` | lesson review, diff-check |

새 paging/cursor 의존성은 추가하지 않는다. 새 예외 assertion은 `io.bluetape4k.assertions.assertFailsWith`만 사용하고 JUnit/Kotlin `assertThrows` 계열은 도입하지 않는다. 기존 모듈 소유권과 dirty 변경은 되돌리지 않는다.

## Task 1: capability 계약 RED 테스트

복잡도: 중간. 선행: 설계 문서.

Files:

- Modify: `GraphCapabilitiesTest.kt`, `GraphBatchOperationsTest.kt`
- Modify: `AbstractGraphCapabilityConformanceTest.kt`

- [x] Step 1: `BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`가 별도 enum으로 존재하고, 기존 `CHUNKED_*`에는 `api-chunking-only`, bounded capability에는 `native-traversal-bounded` 제약이 필요하다는 실패 테스트를 작성한다.
- [x] Step 2: marker 없는 기본 repository/decorator가 기존 `CHUNKED_*`만 계산하고 bounded flag를 계산하지 않는 RED를 확인한다. 예외 경계는 `bluetape4k-assertions.assertFailsWith`를 유지한다.
- [x] Step 3: conformance fixture에 `boundedChunked` 기대값을 추가하고, 기대 backend만 bounded 두 capability를 요구하도록 RED를 확인한다.

검증 명령:

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphCapabilitiesTest' --tests '*GraphBatchOperationsTest' --no-build-cache --console=plain
```

예상 결과는 새 enum/marker가 없어 compile 또는 assertion 실패하는 것이다.

## Task 2: graph-core 최소 구현

복잡도: 중간. 선행: Task 1.

Files:

- Modify/Create: `GraphCapabilities.kt`와 같은 package의 bounded marker source

- [x] Step 1: `GraphCapability`에 새 bounded 두 값을 추가하고 기존 enum/API version을 제거하지 않는다.
- [x] Step 2: `GraphBoundedChunkOperations`의 한국어 KDoc에 “소스 결과 전체를 먼저 materialize하지 않는 구현자의 명시적 계약”을 기록한다.
- [x] Step 3: `GraphCapabilities.from`이 repository interface에는 API capability만, bounded marker에는 bounded capability와 native constraint만 추가하도록 구현한다. 기본 chunk fallback은 동작과 반환 형식을 바꾸지 않는다.
- [x] Step 4: `CHUNKED_EXPORT` KDoc의 bounded 표현을 API chunk 의미로 고친다.

검증 명령:

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphCapabilitiesTest' --tests '*GraphBatchOperationsTest' --no-build-cache --console=plain
```

## Task 3: TinkerGraph bounded projection

복잡도: 중간. 선행: Task 2.

Files:

- Modify: `TinkerGraphOperations.kt`, `TinkerGraphSuspendOperations.kt`
- Modify: TinkerGraph capability/conformance/chunk tests as required

- [x] Step 1: 기존 traversal iterator 기반 sync/suspend chunk 경로와 chunk 크기·순서 테스트를 먼저 보존한다.
- [x] Step 2: 두 구현에 marker를 적용하고 virtual-thread adapter가 delegate capability를 그대로 투영하는지 테스트한다.
- [x] Step 3: TinkerGraph만 bounded 두 capability를 보고하는 GREEN을 확인한다. AGE/Neo4j/Memgraph/FalkorDB sync 구현에는 marker를 추가하지 않는다.

검증 명령:

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphCapabilityConformanceTest' --tests '*TinkerGraphOperationsTest' --no-build-cache --console=plain
```

## Task 4: backend conformance와 consumer 의미 고정

복잡도: 중간. 선행: Task 3.

Files:

- Modify: 공용 conformance fixture 및 AGE/Neo4j/Memgraph/FalkorDB/TinkerGraph의 expected capability setup
- Inspect/Modify: graph-io CSV/GraphML consumer KDoc이 API chunk를 bounded로 오해하지 않는 범위

- [x] Step 1: 모든 backend가 기존 chunk 결과 크기·순서 계약을 유지하는지 fixture assertion을 실행한다.
- [x] Step 2: 네 container backend는 `CHUNKED_*`와 `api-chunking-only`만 보고하고 bounded capability를 보고하지 않는지 확인한다.
- [x] Step 3: TinkerGraph는 bounded capability와 `native-traversal-bounded`를 확인하고, default list fallback은 bounded가 아님을 core test로 고정한다.
- [x] Step 4: graph-io exporter 호출 경로는 유지하되, capability를 확인하지 않은 호출자에게 heap bound를 암시하는 KDoc을 추가하지 않는다.

검증은 Testcontainers가 필요한 backend를 동시에 실행하지 않고 AGE → Neo4j → Memgraph → FalkorDB 순서로 수행한다. 실패 시 이미지, lifecycle, 재시도 여부를 별도 기록하며 skipped를 성공으로 취급하지 않는다.

## Task 5: 영어/한국어 문서와 KDoc 정렬

복잡도: 낮음. 선행: Task 2–4.

Files:

- Modify: root `README.md`, `README.ko.md`
- Modify: `graph/graph-core/README.md`, `README.ko.md`
- Modify: `graph-io/graphml/README.md`, `README.ko.md`

- [x] Step 1: `CHUNKED_*`는 API chunking일 뿐이라고 명시한다.
- [x] Step 2: `BOUNDED_CHUNKED_READ`/`BOUNDED_CHUNKED_EXPORT`를 확인해야 source heap bound를 요구할 수 있다고 설명한다.
- [x] Step 3: 현재 TinkerGraph만 bounded reference path로, AGE/Neo4j/Memgraph/FalkorDB sync는 호환 list fallback으로 명시한다.
- [x] Step 4: GraphML의 “never materializes complete list” 단정을 capability 조건부 문장으로 교체하고 영어/한국어 locale 의미를 대조한다.

문서에는 지원하지 않는 backend를 지원한다고 쓰지 않으며, `git diff --check`와 한국어 용어 audit를 통과시킨다.

## Task 6: 정적 검증·독립 7-Tier review

복잡도: 높음. 선행: Task 1–5.

- [x] Step 1: affected graph-core/graph-tinkerpop tests와 compile을 실행한다.
- [x] Step 2: graph-core 및 TinkerPop detekt, ABI 관련 compile/static 검사를 실행한다.
- [x] Step 3: `git diff --check`와 변경 경로/문서 grep을 실행한다.
- [ ] Step 4: 독립 architecture/code-reviewer에게 exact HEAD를 전달해 7-Tier P0/P1/P2, ABI, capability matrix, assertions, docs, regression risk를 source-read-only로 재검토시킨다.
- [ ] Step 5: P0/P1 발견 시 수정 후 같은 exact HEAD 기준으로 재검토하고, P2는 후속 이슈 후보로 기록한다.

## Task 7: lesson, receipt, Lore commit

복잡도: 중간. 선행: Task 6.

- [ ] Step 1: `docs/lessons/2026-08-25-issue-536-bounded-chunk-capability.md`에 문제, 선택한 분리 계약, backend 제한, 검증 명령과 잔여 P2를 한국어로 기록한다.
- [ ] Step 2: workflow helper로 각 topology check의 입력·결과·component evidence·completion을 기록한다. helper 외 `.bluetape` 상태를 직접 수정하지 않는다.
- [ ] Step 3: implementation과 lesson을 Lore trailers가 있는 commit으로 기록하고 `git status`, `git diff --check`, exact HEAD를 다시 읽는다.

롤백은 implementation/lesson commit을 각각 revert하여 기존 enum·fallback·문서 기준선으로 돌아가는 경로를 사용한다. PR 생성, push, merge, issue close는 이 계획의 범위가 아니다.

## DoD

- [ ] API chunking과 bounded 실행이 별도 capability/constraint로 기계적으로 구분된다.
- [ ] TinkerGraph sync/suspend/virtual-thread projection만 bounded를 광고하고 네 container sync backend는 명시적으로 광고하지 않는다.
- [ ] core 기본 fallback, backend conformance, chunk 크기·순서가 `bluetape4k-assertions` 기반 테스트로 검증된다.
- [ ] root/graph-core/GraphML 영어·한국어 문서가 heap bound의 조건과 제한을 정확히 설명한다.
- [ ] compile, detekt, targeted/full affected tests, 순차 container conformance, diff-check, 독립 7-Tier review가 fresh evidence로 기록된다.
- [ ] open issue #536과 feature branch 상태를 보존하고 PR/merge 없이 종료한다.

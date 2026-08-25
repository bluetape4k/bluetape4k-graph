# 이슈 #543 TinkerGraph ID 계약과 weighted-path assertion 7-Tier 리뷰

## 리뷰 범위와 근거

- 대상: `graph/graph-tinkerpop`의 동기·suspend TinkerGraph ID 입력 경로와 weighted-path 테스트 assertion
- 이슈: [#543](https://github.com/bluetape4k/bluetape4k-graph/issues/543)
- 기준 커밋: `67b2f872920924f16971dc62477481d52dec785c0` (`develop`)
- 변경 파일:
  - `TinkerGraphOperations.kt`
  - `TinkerGraphOperationsTest.kt`
  - `TinkerGraphSuspendOperationsTest.kt`
  - `TinkerGraphWeightedPathTest.kt`
  - `TinkerGraphCapabilityConformanceTest.kt`
- 검토 근거: 소스 diff, TinkerGraph 테스트 77개, `compileKotlin`, `detekt`, `git diff --check`
- 입력 계약: malformed ID는 `GraphQueryException`, 숫자 형식이지만 존재하지 않는 ID는 기존 null/false/empty 결과를 유지한다.

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
| --- | --- | --- | --- |
| 1 | 빌드·API·ABI | `requireNumericId`는 private helper이고 repository 공개 시그니처를 변경하지 않았다. `compileKotlin` 성공. | PASS |
| 2 | 동작·계약 | 정점/간선 CRUD, merge, neighbors, shortest/all paths, weighted Dijkstra/A*, degree centrality, BFS/DFS의 ID 진입점이 같은 예외 계약을 사용한다. 숫자형 missing ID는 absence semantics를 유지한다. | PASS |
| 3 | 테스트·assertion | 동기·suspend 회귀 테스트와 conformance 테스트가 malformed/missing을 분리한다. weighted-path custom helper의 bare `assert`를 `shouldBeEqualTo`로 교체해 JVM `-ea`와 무관하게 검증한다. | PASS |
| 4 | 동시성·coroutine | TinkerGraph suspend 구현은 sync delegate를 사용하며 이번 변경은 lock, transaction rollback 상태 보존, cancellation 경계를 건드리지 않는다. 기존 suspend 테스트와 신규 회귀가 통과한다. | PASS |
| 5 | Bluetape4k 패턴·생태계 | `io.bluetape4k.assertions.assertFailsWith`와 `shouldContain`/`shouldBeNull`/`shouldBeEqualTo`를 사용했다. 다른 numeric-ID backend와 같은 `GraphQueryException` 계열 계약을 따른다. | PASS |
| 6 | 문서·호환성 | 공개 API·README·BOM을 변경하지 않는 내부 계약 정렬이다. 이 리뷰와 lesson에 결정·범위·검증·후속 guard를 기록했다. | PASS |
| 7 | 운영·CI·릴리스 | in-memory TinkerGraph라 Testcontainers 검증은 해당 없음이다. module test/compile/detekt는 통과했으며 PR·CI dispatch·push·release는 사용자 범위 밖이라 실행하지 않았다. | PASS (N/A 경계 포함) |

## 심각도별 findings

- P0: 없음
- P1: 없음
- P2: 없음
- P3: 없음

## 추적성과 잔여 위험

- `GraphElementId` 자체는 문자열 value class이므로 backend가 numeric-ID 입력 계약을 명시적으로 검사해야 한다. 이 변경은 TinkerGraph 경계에서만 조기 거부하며 공통 core 모델을 확장하지 않는다.
- backend 간 전체 conformance matrix 실행과 PR CI는 이번 로컬 이슈 작업의 범위가 아니다. 해당 표면은 PR 생성 승인 후 exact-head 기준으로 다시 검증해야 한다.

## Writer DoD (SPW)

- SPW-01: PASS — 독자, 이슈, 대상 파일, 기준 커밋, 테스트·정적분석 근거를 고정했다.
- SPW-02: PASS — 리뷰 범위, 7개 tier, severity, 근거, disposition, gap, verdict를 포함했다.
- SPW-03: PASS — 한국어 기술 문체와 API/명령어/식별자 보존을 확인했다. KO-01~KO-06을 문장 단위로 읽었고, 수치·불확실성·N/A 경계를 바꾸지 않았다.
- SPW-04: PASS — source diff와 테스트 결과를 대조해 모든 계약 주장을 확인했다.
- SPW-05: PASS — 최종 Markdown을 read-back했고 표·코드 토큰·링크·헤딩 렌더링을 확인했다. KO-07 terminology audit는 이 저장소의 review/lesson 경로에 적용할 충돌 규칙이 없어 N/A로 기록한다.

## Verdict

7-Tier 기준 blocker와 미해결 finding이 없어 이슈 #543의 로컬 구현·검증 단계는 PASS다. PR·merge·release 단계는 실행하지 않았다.

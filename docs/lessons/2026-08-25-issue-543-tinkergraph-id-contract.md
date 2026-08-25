# 이슈 #543 lesson: TinkerGraph numeric ID 입력 계약을 단일화한다

## Context

TinkerGraph는 `GraphElementId`를 `Long`으로 변환할 수 없을 때 malformed 입력을 null, false, 빈 목록으로 처리하거나 일부 알고리즘에서 `IllegalArgumentException`을 던졌다. 같은 호출 표면에서 입력 오류와 valid-but-missing 리소스가 섞여 backend 간 계약이 달라졌다. weighted-path 테스트의 custom helper도 Kotlin bare `assert`에 의존해 JVM assertions가 비활성화되면 검증이 사라졌다.

## Decision

`TinkerGraphOperations`의 모든 외부 numeric-ID 진입점은 private `requireNumericId`를 통과시킨다. 변환 실패는 `GraphQueryException("TinkerGraph requires numeric ID, got: ...")`로 조기에 거부하고, 변환 가능한 missing ID는 기존 null/false/empty 결과를 유지한다. weighted `shortestPath`와 `aStarPath`도 fallback 호출 전에 같은 검사를 수행한다.

테스트는 동기·suspend repository, traversal/algorithm 표면, backend conformance lane에서 malformed/missing을 각각 검증한다. weighted-path 순서 helper는 `bluetape4k-assertions`의 `shouldBeEqualTo`로 바꿔 JVM `-ea` 여부와 무관하게 실패하도록 했다.

## Outcome and verification

- RED: production helper를 적용하기 전 신규 malformed-ID 테스트 3개가 “Expected GraphQueryException but no exception was thrown”으로 실패했고 valid-missing 테스트는 통과했다.
- GREEN: TinkerGraph 대상 테스트 77개 통과, `BUILD SUCCESSFUL`.
- 정적 검증: `:bluetape4k-graph-tinkerpop:compileKotlin`, `:bluetape4k-graph-tinkerpop:detekt`, `git diff --check` 통과.
- 회귀 범위: sync/suspend CRUD·merge·neighbors·shortest/all paths·Dijkstra/A*·degree centrality·BFS/DFS와 conformance contract를 포함했다.
- 부모 PR CI 수리: 기존 endpoint/virtual-thread absence fixture가 malformed 문자열을 사용해 `GraphQueryException`을 기대한 `IllegalArgumentException` 계약과 충돌했다. 네 테스트를 valid-but-missing numeric ID(`99999999`)로 교체했다.
- 수리 GREEN: graph-core targeted 19개, graph-core 전체 349개, TinkerGraph 전체 113개 테스트와 compile/detekt가 통과했다.

## Miss / surprise

첫 RED 실행에서는 graph-core test-fixtures jar가 이전 Gradle cache 결과라 conformance fixture가 classpath에 없었다. `:bluetape4k-graph-core:compileTestFixturesKotlin :bluetape4k-graph-core:testFixturesJar --rerun-tasks`로 fixture 산출물을 재생성한 뒤 RED를 다시 실행했다. 따라서 fixture compile 문제를 assertion 실패와 혼동하지 않도록 테스트 기반을 먼저 확인해야 한다.

## Future guard

- numeric-ID backend를 추가하거나 수정할 때 malformed 입력과 valid-but-missing 입력을 같은 테스트 케이스에서 분리한다.
- fallback algorithm을 호출하는 메서드는 fallback 내부의 우연한 조회 순서에 의존하지 말고 public entrypoint에서 입력 계약을 먼저 검사한다.
- 순서·집합 assertion에는 Kotlin bare `assert` 대신 `io.bluetape4k.assertions`를 사용한다.
- Gradle test-fixtures를 새 conformance lane에 연결할 때는 fixture jar에 기대 class가 포함됐는지 확인한 뒤 RED/ GREEN을 판정한다.

## Writer DoD

- SPW-01: PASS — 이슈 #543, PR #564, 최초 CI 실패, 수리 파일, exact-head 재검증 대기를 근거로 고정했다.
- SPW-02: PASS — context, decision, outcome, miss, future guard, 검증과 남은 gate를 포함했다.
- SPW-03: PASS — 한국어 기술 문체와 코드 토큰을 보존했고 terminology audit에서 2개 파일, findings 0을 확인했다.
- SPW-04: PASS — 현재 테스트 수와 PR 상태를 소스·실행 결과와 대조했다.
- SPW-05: PASS — 최종 Markdown을 read-back하고 headings, code tokens, status boundary를 확인했다.

## DoD

- Issue #543 acceptance criteria: 충족
- 7-Tier review: blocker 없음
- PR #564 exact-head CI: 수리 후 재검증 대기
- merge·release: fresh merge approval 전까지 실행하지 않음

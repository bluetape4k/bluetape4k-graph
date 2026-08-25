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

## Miss / surprise

첫 RED 실행에서는 graph-core test-fixtures jar가 이전 Gradle cache 결과라 conformance fixture가 classpath에 없었다. `:bluetape4k-graph-core:compileTestFixturesKotlin :bluetape4k-graph-core:testFixturesJar --rerun-tasks`로 fixture 산출물을 재생성한 뒤 RED를 다시 실행했다. 따라서 fixture compile 문제를 assertion 실패와 혼동하지 않도록 테스트 기반을 먼저 확인해야 한다.

## Future guard

- numeric-ID backend를 추가하거나 수정할 때 malformed 입력과 valid-but-missing 입력을 같은 테스트 케이스에서 분리한다.
- fallback algorithm을 호출하는 메서드는 fallback 내부의 우연한 조회 순서에 의존하지 말고 public entrypoint에서 입력 계약을 먼저 검사한다.
- 순서·집합 assertion에는 Kotlin bare `assert` 대신 `io.bluetape4k.assertions`를 사용한다.
- Gradle test-fixtures를 새 conformance lane에 연결할 때는 fixture jar에 기대 class가 포함됐는지 확인한 뒤 RED/ GREEN을 판정한다.

## DoD

- Issue #543 acceptance criteria: 충족
- 7-Tier review: blocker 없음
- PR·merge·release: 사용자 범위 밖, 실행하지 않음

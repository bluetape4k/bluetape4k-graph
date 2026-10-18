# PR #656 로그 안전성 수정에서 paired API 경로를 함께 검증한다

## 맥락

PR #656의 독립 검토에서 그래프 알고리즘과 Apache AGE의 중복 그래프 생성 로그가 민감한 값을 포함할 수 있는지 확인했다. `GraphVertex`에는 `properties`가 있으므로 `GraphVertex` 전체 객체를 문자열 보간하면 정점 속성 값이 로그로 노출될 수 있다. Apache AGE는 동기 API와 coroutine API를 함께 제공하므로 두 구현의 동일한 예외 경로를 모두 확인해야 했다.

## 판단과 발견

- `AStarRunner`와 `DijkstraRunner`는 순회 중인 `GraphVertex` 전체 객체를 로그에 넣지 않고 고정된 DEBUG 메시지인 `current vertex loaded`를 기록한다.
- AGE 동기 경로와 suspend 경로의 중복 그래프 생성 처리는 모두 `Duplicate AGE graph creation ignored`를 DEBUG로 기록한다. 그래프 이름과 예외 상세는 메시지에 포함하지 않는다.
- suspend 경로 테스트는 수정 전에 그래프 이름과 `PSQLException` 상세를 기록하던 기존 동작을 잡아 RED가 되었다. 동기 경로만 수정하면 paired API의 누락을 놓칠 수 있다는 사례다.

## 결과

로그에 전체 `GraphVertex` 객체와 AGE 중복 생성 예외의 이름·상세를 남기지 않도록 두 알고리즘과 두 AGE API 경로를 정렬했다. 중복 생성은 기존처럼 허용하되, 운영 로그에는 고정된 DEBUG 메시지만 남긴다.

## 검증

- `AgeGraphSuspendOperationsTest`: targeted test 1개 통과.
- `:bluetape4k-graph-age:test`: 212개 통과.
- `:bluetape4k-graph-age:detekt`: 통과.
- `:bluetape4k-graph-core:test`: 428개 통과, 기존 비활성화된 `BfsDfsRunnerTest` 6개 pending.
- `:bluetape4k-graph-core:detekt`: 통과.
- 새 PR head의 GitHub exact-head CI는 아직 완료되지 않았다.

## 다음 작업 지침

동기 API의 로그를 수정할 때는 대응하는 suspend 또는 coroutine API의 동일한 예외 경로를 함께 검색한다. `GraphVertex`처럼 `properties`를 포함하는 객체는 전체 보간 로그를 피하고, 로그 안전성 테스트는 고정 메시지·로그 레벨·그래프 이름·예외 상세의 미포함을 함께 검증한다.

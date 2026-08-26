# #535 AGE suspend JDBC Flow 구현 계획

## 순서

1. **기준선과 계약 고정**
   - `AgeGraphSuspendOperations.kt`, 테스트, 양국 README를 다시 읽는다.
   - AGE `AgeGraphSuspendOperationsTest` 기준 실행과 Testcontainers 상태를
     기록한다.
   - 산출물: 설계 문서의 source ledger와 기준선 결과.

2. **회귀 테스트를 먼저 추가**
   - 직접 조회 Flow에서 `first()`/`take(1)`를 반복 수집한 뒤 후속 count query가
     제한 시간 안에 완료되는 취소·connection 반환 테스트를 추가한다.
   - collector 예외 전파가 유지되고 후속 query가 동작하는 실패 경로를
     추가한다.
   - 기존 transaction-scoped Flow materialize 테스트는 유지한다.
   - 예외 assertion은 `io.bluetape4k.assertions.assertFailsWith`만 사용한다.

3. **최소 구현**
   - `newSuspendedTransaction` 기본 context를 `Dispatchers.IO`로 고정한다.
   - `BlockingExecutable`에서 positive `PreparedStatement.fetchSize`를 설정하고,
     streaming transaction의 `maxAttempts`를 1로 제한한다.
   - Exposed callback과 `channelFlow` 사이에 `trySendBlocking` 기반 `streamQuery`를
     추가한다.
   - 여섯 직접 조회를 helper로 전환하고 알고리즘 delegate 경계는 변경하지
     않는다.
   - 취소를 삼키는 예외 처리나 새 dependency를 추가하지 않는다.

4. **문서와 정적 검증**
   - Korean KDoc과 graph-age README 두 locale에 직접 Flow의 cursor/fetch size/
     backpressure 및 transaction-scoped materialize 차이를 반영한다.
   - `git diff --check`, `detekt`, compile, assertions/`runCatching`/blocking
     call 정적 검색과 terminology audit를 실행한다.

5. **통합 검증과 7-Tier review**
   - AGE Testcontainers를 다른 graph backend와 병렬 실행하지 않고 순차적으로
     targeted → module test 순서로 실행한다.
   - Tier 1 계약/호환성, Tier 2 API/패턴, Tier 3 lifecycle/취소, Tier 4
     concurrency/backpressure, Tier 5 테스트/fixture, Tier 6 문서/운영,
     Tier 7 diff/회귀/정적 검사를 source-read-only로 확인한다.
   - P0/P1은 반드시 수정한다. 남는 P2/P3는 Korean GitHub issue로 생성하고
     현재 issue와 연결한다.

6. **완료 증거와 커밋**
   - workflow receipt에 각 required check와 fresh main verification을 붙인다.
   - `docs/review/issue-535-age-suspend-flow-7tier.md`와 lesson을 read-back한다.
   - Lore trailers를 포함한 Korean commit을 만들되 PR/merge/push/issue close는
     수행하지 않는다.

## 롤백과 재실행

- 실패 시 `streamQuery` helper 전환만 되돌리고 public signature와 기존
  transaction materialize 계약은 유지한다.
- container/네트워크 실패는 코드 실패로 분류하지 않고 lifecycle 증거를
  기록한 뒤 동일 command를 순차 재실행한다.
- 취소 테스트가 flaky하면 ad hoc thread를 늘리지 말고
  `runSuspendIO`와 실제 Flow cancellation 경로를 먼저 재현한다.

## 예상 증거

- targeted 및 전체 `:bluetape4k-graph-age:test` 결과와 테스트 수
- `:bluetape4k-graph-age:detekt`, compile, `git diff --check`
- 변경 전후 list materialization 검색 결과
- 취소 반복 후 후속 query 성공 로그
- 7-Tier review, Korean terminology audit, workflow receipt completion

# #535 AGE suspend JDBC Flow 설계

## 문제와 범위

`AgeGraphSuspendOperations`의 직접 조회 Flow는 Exposed JDBC `ResultSet`을
먼저 `MutableList`로 모두 읽은 뒤 collector에 전송한다. 이 구조는 결과 수에
따라 메모리가 증가하고, collector가 첫 항목만 소비하거나 취소해도 조회가
끝날 때까지 JDBC 작업이 계속된다. 또한 `newSuspendedTransaction`의 기본
context가 호출자 context라서 직접 suspend JDBC 연산의 실행 경계가 코드에서
명확하지 않다.

이번 변경은 다음 직접 조회만 대상으로 한다.

- `findVerticesByLabel`
- `findEdgesByLabel`
- `findEdgesByStartId`
- `findEdgesByEndId`
- `neighbors`
- `allPaths`

알고리즘 Flow는 이미 동기 delegate를 `Dispatchers.IO`에서 호출하며, 이
이슈에서는 별도 cursor 계약을 만들지 않는다. `suspendTransaction { ... }`의
transaction-scoped Flow는 commit 전에 소비해야 하므로 현재
`materializeTransactionResult` 계약을 유지한다. 트랜잭션이 끝난 뒤 JDBC
cursor를 노출하는 것은 Exposed transaction 소유권을 벗어나므로 직접 facade
Flow의 lazy 계약과 구분해 문서화한다.

## 제안 설계

1. `newSuspendedTransaction`의 기본 context를 `Dispatchers.IO`로 고정한다.
   `withContext`는 호출자의 `Job`을 유지하므로 취소 전파는 유지되고, JDBC
   blocking 호출은 caller dispatcher에서 실행되지 않는다.
2. 직접 조회를 공통 `streamQuery` helper로 모은다. helper는
   `channelFlow` 안에서 `newSuspendedTransaction(Dispatchers.IO)`를 시작하고,
   `BlockingExecutable`로 실제 `PreparedStatement.fetchSize`를 positive 값으로
   설정한 뒤 `ResultSet`을 행 단위로 읽는다. `DatabaseConfig.defaultFetchSize`가
   positive면 이를 사용하고, 없으면 100을 기본값으로 사용한다.
3. callback은 suspend 함수가 아니므로 `kotlinx.coroutines.channels.trySendBlocking`
   을 사용해 channel backpressure를 적용한다. 이 bridge는 IO worker에서만
   실행하고, `channelFlow`의 collector 취소·실패가 callback 예외로 전파되게
   한다.
4. streaming transaction은 행을 이미 collector에 공개한 뒤 재시도할 수 없으므로
   `maxAttempts=1`로 고정한다. Exposed 실행 경계는 `ResultSet.use`와
   transaction 종료 시 connection/statement close를 계속 소유하며, 별도
   `runCatching`으로 취소를 삼키지 않는다.

## 실패와 취소 계약

- collector가 `first()` 또는 `take(1)`로 upstream을 취소하면 `trySendBlocking`
  이 종료되고 transaction이 rollback/close 경로를 거친다.
- mapper 또는 collector가 예외를 던지면 예외를 그대로 전파하고
  `ResultSet`/transaction을 닫는다.
- `CancellationException`은 일반 예외로 변환하지 않는다.
- JDBC driver에는 positive fetch size를 전달해 결과 전체를 driver 메모리에
  먼저 적재하지 않도록 한다. 단, `executeQuery()`/`ResultSet.next()` 자체의
  blocking을 `Statement.cancel()`로 중단하는 별도 계약은 이 이슈 범위가 아니다.
- transaction-scoped Flow는 commit 전 materialize를 유지하므로 이 API의
  bounded/lazy 보장은 직접 facade Flow에 한정한다.

## 호환성과 위험

- public method signature와 반환 타입은 바꾸지 않는다.
- `trySendBlocking`은 IO thread를 잠시 점유하므로 channel capacity와 collector
  처리 속도가 application-side bounded memory를 결정한다. JDBC fetch size는
  driver-side prefetch 상한을 둔다. 무제한 producer thread를 추가하지 않는다.
- AGE Testcontainers는 기존 singleton launcher와 순차 실행 규칙을 따른다.
- 취소 후 connection pool이 고갈되지 않는지 반복 `first()`/`take(1)`와 후속
  query로 검증한다.

## 수용 기준과 DoD

- 직접 조회 Flow에서 전체 결과를 담는 `MutableList`를 제거한다.
- 기본 suspend JDBC 경계가 `Dispatchers.IO`로 명시된다.
- 실제 AGE Testcontainers에서 기존 `AgeGraphSuspendOperationsTest`와
  취소·정리 회귀가 통과한다.
- 새 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`를 사용한다.
- `detekt`, compile, `git diff --check`, Korean terminology audit와 7-Tier
  review를 완료하고, 발견된 P2/P3는 별도 이슈로 기록한다.

## 근거와 미확인 사항

- 기준 테스트: `./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphSuspendOperationsTest' --console=plain`이 2026-08-24 기준 통과했다.
- 구현 근거: `AgeGraphSuspendOperations.kt`의 현재 list materialization과
  Exposed `JdbcTransaction.exec` source의 callback-scoped ResultSet close.
- 직접 cursor close hook을 공개 API로 추가하지 않는다. 이는 현재
  `GraphSuspendOperations` 계약에 없는 별도 lifecycle API가 되므로 후속
  요구로 남긴다.

# #552 AGE JDBC statement 취소 수명주기 설계

## 문제와 범위

AGE suspend direct `Flow`는 `Dispatchers.IO`에서 JDBC cursor를 읽지만,
`executeQuery()` 또는 `ResultSet.next()`가 driver 내부에서 블로킹되면 일반적인
coroutine 취소만으로 해당 호출이 즉시 중단되지 않는다. 현재 transaction은
취소된 collector가 반환될 때까지 JDBC connection을 점유할 수 있다.

대상 이슈는 [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)이며,
적층 기준은 PR [#574](https://github.com/bluetape4k/bluetape4k-graph/pull/574)의
exact head `130532a2c2f0be2e9c87572ed6876bbb688afa06`이다. 구현은 PR
[#575](https://github.com/bluetape4k/bluetape4k-graph/pull/575)에서 진행하며,
현재 source head는 `5a21d911` (`35a9bef41daf5176a16695ee48cb15d7584e5344`의
실행 전 race 보정)이다. 다른 backend의
취소 계약과 `suspendTransaction` 중첩 Flow 결과 계약은 각각 기존 slice와
후속 이슈 범위로 남긴다.

## 결정

1. `execStreaming`이 `JdbcPreparedStatementApi`를 `executeInternal` 진입 직후
   `AtomicReference`에 등록한다. 실행 전 `ensureActive()`와
   `statementExecuting` 상태를 분리해 이미 취소된 query는 시작하지 않는다.
   등록은 `executeQuery()`와 `ResultSet.next()` 양쪽이 실행 중인 동안 유지된다.
2. coroutine Job에는 `onCancelling=true` completion handler를 등록한다. 취소가
   시작되면 active statement에 JDBC `cancel()`을 최대 한 번 호출한다. 취소가
   statement 등록과 경합해도 등록 직후 동일한 one-shot guard가 다시 확인한다.
3. driver 호출이 `SQLException`을 먼저 반환하더라도 취소된 Job의
   `ensureActive()`를 호출해 collector가 원래 `CancellationException`을 받도록
   한다. 일반 JDBC 오류는 취소가 아닌 경우 기존 예외를 그대로 유지한다.
4. statement를 직접 닫지는 않는다. Exposed transaction의 기존 cleanup이
   current statement와 `ResultSet`을 각각 닫도록 소유권을 유지하고, test double로
   `cancel`/statement close/ResultSet close 횟수를 관찰한다.
5. JDBC driver가 `Statement.cancel()`을 실제 실행 중인 호출에 적용하지 않거나
   마지막 `ensureActive()`와 `IN_QUERY` 전환을 원자적으로 보장하지 않으면 prompt
   cancellation을 보장하지 않는다. 이 경우 positive Exposed `defaultQueryTimeout`
   또는 vendor API를 별도로 설정해야 하며, AGE facade가 무조건 bounded
   completion을 주장하지 않는다.

## API·호환성

- `AgeGraphSuspendOperations`의 public 메서드와 `GraphSuspendOperations` ABI는
  변경하지 않는다.
- 취소 경계는 AGE의 private `execStreaming` 구현에만 추가한다. 기존 positive
  fetch size, `maxAttempts=1`, channel backpressure와 ResultSet ownership은
  유지한다.
- `kotlinx.coroutines.InternalCoroutinesApi`의 `onCancelling` overload는
  blocking JDBC call을 완료 전에 중단해야 하는 이 구현 경계에서만 opt-in한다.

## 검증 계획

- 실제 AGE Testcontainers와 JDBC proxy를 결합한다.
- `executeQuery()`가 latch에서 대기하는 경우 collector 취소가 bounded하게
  반환되고, `CancellationException`, statement `cancel=1`, statement `close=1`
  을 검증한다.
- `ResultSet.next()`가 latch에서 대기하는 경우 동일한 statement 취소와
  `ResultSet.close=1`을 검증한다.
- statement 실행 전에 취소되는 경우 query가 시작되지 않고
  `executeQuery=0`, `cancel=0`, statement close를 검증한다.
- AGE 전체 테스트, compile, Detekt, 금지 assertion scan, `git diff --check`를
  AGE 단일 backend 범위에서 순차 실행한다.

## 범위 밖

- JDBC driver별 실제 server-side cancel latency와 vendor-specific timeout
  설정은 이 slice에서 일반화하지 않는다.
- Neo4j, Memgraph, TinkerPop의 별도 streaming 구현은 변경하지 않는다.
- Amazon Neptune feasibility는 기존 backlog 계약을 유지한다.

## SPW writer gate

- **SPW-01 — Audience and purpose: PASS.** AGE backend 유지보수자와 reviewer가
  blocking JDBC 취소와 resource ownership을 재현할 수 있도록 범위를 고정했다.
- **SPW-02 — Artifact contract: PASS.** 문제, 결정, API·호환성, 검증 계획과
  범위 밖 항목을 포함한다.
- **SPW-03 — Korean technical register: PASS.** 설명은 한국어이고 code,
  command, API, issue token은 원문을 보존한다.
- **SPW-04 — Technical traceability: PASS.** #552, PR #574 exact base,
  `execStreaming`, Exposed cleanup, 두 blocking test를 연결한다.
- **SPW-05 — Read-back: PENDING.** PR 생성 후 exact head와 hosted evidence를
  GitHub live metadata로 다시 대조한다.

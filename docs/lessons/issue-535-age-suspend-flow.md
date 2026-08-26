# #535 AGE suspend JDBC Flow lesson

## Context

`AgeGraphSuspendOperations`의 직접 조회 Flow가 JDBC `ResultSet`을 먼저
`MutableList`로 materialize해 collector에 전달하고 있었다. 이 구조는 큰 결과의
메모리 사용량과 `first()`/취소 시 작업 지속 시간을 결과 크기에 묶었다. #535의 목표는
직접 Flow를 `Dispatchers.IO`의 bounded cursor 경계로 정렬하고, transaction-scoped
Flow의 commit 전 materialize 계약은 유지하는 것이었다.

## Decision

- 여섯 direct query를 `streamQuery`로 통합한다.
- Exposed 문자열 `exec` 대신 private `BlockingExecutable`을 사용해
  `DatabaseConfig.defaultFetchSize` 또는 positive fallback 100을
  `PreparedStatement.fetchSize`에 설정한다.
- callback과 `channelFlow` 사이에 `trySendBlocking`을 두고, collector 취소·예외는
  ResultSet/statement/transaction close 경계로 전파한다.
- streaming transaction은 행을 이미 공개한 뒤 재시도할 수 없으므로 `maxAttempts=1`로
  고정한다. 일반 suspend transaction의 retry 동작은 변경하지 않는다.
- 테스트 fixture는 명시적 `Database`와 `defaultFetchSize=8`을 사용하고 256/128행
  결과로 early cancellation과 collector failure 경로를 압박한다.

## Outcome

- public constructor/interface/signature와 ABI는 유지됐다.
- `AgeGraphSuspendOperationsTest` targeted `29/29`, fresh graph-age full `191/191`
  (failures/errors/skipped `0`)이 통과했다.
- `detekt`, `compileKotlin`, `compileTestKotlin`, `git diff --check`, Korean
  terminology audit가 통과했다.
- 독립 architecture/code 7-Tier review에서 P0/P1 및 merge blocker는 남지 않았다.
- 최종 상태는 `PASS`이며 Architectural Status는 비차단 `WATCH`다.

## Miss and surprise

초기 구현은 channel backpressure만 추가하고 JDBC driver fetch size를 설정하지 않아
pgjdbc 기본 fetch-all 위험을 남겼다. 또한 Exposed 기본 transaction retry가 late
`SQLException`에서 이미 방출한 prefix를 다시 내보낼 수 있었다. 두 P1을 exact HEAD
`48fe03004b046a362468f2771323809b265d9505`에서 `BlockingExecutable`과 `maxAttempts=1`로
수정하고 재검토했다.

정상 Testcontainers 경로에서는 statement property와 late-failure retry를 직접 관찰하지
않았고, JDBC `executeQuery()`/`ResultSet.next()`가 stall한 동안 `Statement.cancel()`로
취소하는 계약도 검증하지 않았다. 이 gaps는 구현 결함으로 숨기지 않고 후속 이슈로
분리했다.

## Future guard

- [#550](https://github.com/bluetape4k/bluetape4k-graph/issues/550): fetch size와
  retry 단일 시도의 fault-injection 검증
- [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551): core/cross-backend
  nested `Flow` escape 계약 단일화
- [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552): JDBC stall과
  `Statement.cancel()` lifecycle 검증

다음 AGE JDBC Flow 변경은 #550의 statement/retry 관찰 증거와 #552의 취소 경계를
갱신하지 않으면 bounded/cancellation 계약을 PASS로 판정하지 않는다. transaction-scoped
Flow 계약을 변경할 때는 #551의 cross-backend inventory를 먼저 완료한다.

## SPW writer gate

- **SPW-01: PASS.** 대상 독자는 AGE 유지보수자이며, 구현 결정·실패·검증·후속 guard를
  exact commit과 source/test evidence에 연결했다.
- **SPW-02: PASS.** context/decision/outcome/miss/future guard 구조를 갖췄다.
- **SPW-03: PASS.** Korean technical register와 `Flow`, `ResultSet`, `Statement.cancel()`
  등 identifier 보존을 확인했고 terminology audit findings `[]`를 사용했다.
- **SPW-04: PASS.** 설계/계획, 구현, targeted/full test, independent review,
  후속 issue를 상호 대조했다.
- **SPW-05: PASS.** 최종 Markdown을 read-back하고 review artifact 및 workflow receipt와
  verdict/evidence를 일치시킨다.

# #552 AGE JDBC statement 취소 수명주기 lesson

## 상황

AGE suspend direct `Flow`는 JDBC 작업을 `Dispatchers.IO`에서 수행하므로,
collector Job이 취소되어도 blocking `executeQuery()`나 `ResultSet.next()`는
일반적인 cooperative cancellation 지점에 도달하지 않을 수 있다. 이 상태에서
statement를 놓치면 connection pool 반환이 늦어지고 다음 요청이 대기한다.

## 결정

`execStreaming`이 Exposed `executeInternal` 경계에서 active
`JdbcPreparedStatementApi`를 보관한다. Job의 `onCancelling=true` handler가
취소 시작을 즉시 관찰해 `cancel()`을 one-shot으로 호출하고, statement 등록과
실행 전 취소의 race는 `ensureActive()`와 `statementExecuting` 경계로 분리한다.
driver call이 `SQLException`으로 먼저 끝나면 `ensureActive()`가 원래
`CancellationException`을 다시 전파한다.

statement/ResultSet 직접 close는 Exposed transaction cleanup에 맡긴다. 따라서
취소 경로가 소유권을 중복하지 않고, test double은 `cancel=1`, statement
`close=1`, ResultSet `close=1`을 관찰한다.

## 검증에서 얻은 교훈

- 일반 `Job.invokeOnCompletion { ... }`은 blocking coroutine이 완료될 때까지
  호출되지 않는다. blocking JDBC를 중단하려면 `onCancelling=true` overload가
  필요하다.
- JDBC cancel이 SQL 예외를 먼저 반환하더라도 coroutine 취소 계약을 SQL 예외로
  바꾸면 안 된다. `CoroutineContext.ensureActive()`로 원래 취소를 복원해야
  collector가 일관된 `CancellationException`을 받는다.
- cancellation handler에서 statement를 직접 닫으면 Exposed cleanup과 ownership이
  겹칠 수 있다. cancel은 facade가, close는 transaction owner가 담당하도록
  경계를 분리한다.
- driver가 `Statement.cancel()`을 지원한다는 가정은 이식 가능한 API 계약이
  아니다. 지원하지 않는 driver에는 timeout/vendor API가 필요하다는 제한을
  문서화하고, 테스트 double의 성공을 실제 driver latency 보장으로 과장하지
  않는다.
- 표준 JDBC는 마지막 `ensureActive()`와 driver의 `IN_QUERY` 전환을 원자적으로
  노출하지 않는다. 따라서 `IDLE → IN_QUERY` handoff 경합은 driver cancel 또는
  positive Exposed `defaultQueryTimeout`/vendor API가 설정된 경우에만 bounded
  계약으로 다루고, facade가 universal guarantee를 주장하지 않는다.

## 재현 명령과 결과

```bash
./gradlew :bluetape4k-graph-age:test \
  --tests '*executeQuery가 블로킹 중이어도*' \
  --tests '*ResultSet next가 블로킹 중이어도*' \
  --tests '*JDBC 실행 전에 취소되면*' \
  --no-daemon --console=plain
```

결과는 targeted 테스트 `3/3` 통과이며, `executeQuery` 경로는 원래
`CancellationException`과 statement cancel/close를, `ResultSet.next` 경로는
statement cancel/close와 ResultSet close를 각각 한 번씩 검증했다. 실행 전 취소
경로는 `executeQuery=0`, `cancel=0`, statement close를 검증했다. AGE 전체는
`198/198`, Detekt, 금지 assertion scan, `git diff --check`를 통과했다.

## 남은 범위

AGE 실제 이미지와 proxy는 JDBC lifecycle을 검증하지만, 모든 PostgreSQL/AGE
driver 버전의 cancel latency나 표준 JDBC의 `IDLE → IN_QUERY` handoff를 증명하지
않는다. positive Exposed `defaultQueryTimeout` 또는 vendor-specific API가 없으면
driver가 cancel을 무시하는 경로가 남는다. PR
[#575](https://github.com/bluetape4k/bluetape4k-graph/pull/575)의 hosted exact-head
checks와 review read-back은 아직 후속 증거다.

## SPW writer gate

- **SPW-01 — Audience and purpose: PASS.** 후속 maintainer가 blocking
  cancellation의 원인과 ownership 경계를 재현할 수 있도록 작성했다.
- **SPW-02 — Evidence contract: PASS.** 결정, race/exception lesson, 재현 명령,
  driver 제한과 남은 검증을 포함한다.
- **SPW-03 — Korean register: PASS.** reader-facing 설명은 한국어이며 code,
  command, API, issue token은 원문을 보존한다.
- **SPW-04 — Traceability: PASS.** #552, #574, `execStreaming`, 두 test
  double 경계를 연결한다.
- **SPW-05 — Read-back: PENDING.** 최종 PR exact head와 hosted 결과를 PR 생성
  후 다시 대조한다.

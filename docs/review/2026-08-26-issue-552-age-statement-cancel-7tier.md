# #552 AGE JDBC statement 취소 수명주기 7-Tier 검토

## 검토 범위와 기준

- 대상 이슈: [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)
- 대상 모듈: `graph-age`
- 기준 base: PR [#574](https://github.com/bluetape4k/bluetape4k-graph/pull/574)의
  live exact head `130532a2c2f0be2e9c87572ed6876bbb688afa06`.
- 대상 PR: [#575](https://github.com/bluetape4k/bluetape4k-graph/pull/575).
  구현 관찰 head는 `5a21d911` (`35a9bef41daf5176a16695ee48cb15d7584e5344`의
  후속 race 보정)이고, 이전 read-back docs commit은
  `093f2a7cd09a3191965a869acc34ff5883d92379`이다. 최종 live head는 GitHub
  metadata를 권위로 삼는다.
- 구현은 `AgeGraphSuspendOperations.execStreaming`의 active JDBC statement
  등록, `onCancelling=true` handler, one-shot `cancel()`과 cancellation
  exception 복원으로 제한한다.
- 실제 PR head는 live metadata에서 대조했으며 hosted checks/review read-back은
  아직 대기 중이므로 SPW-05는 PENDING이다.

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
|---|---|---|---|
| 1. 계약·호환성 | public API/ABI와 stacked base | public graph API 시그니처는 유지하고 AGE private streaming 경계만 수정한다. #574 exact head 위에 적층한다. | PASS |
| 2. Kotlin 패턴·API | coroutine cancellation과 assertions | `onCancelling=true`로 blocking call 완료 전 취소를 관찰하고, `AtomicReference`/one-shot guard로 경합을 제어한다. 새 예외 테스트는 `io.bluetape4k.assertions.assertFailsWith`를 사용한다. | PASS |
| 3. Lifecycle·취소 | statement/ResultSet ownership | active statement에는 `cancel()`만 전달하고 Exposed의 기존 transaction cleanup이 statement와 ResultSet close를 소유한다. `ensureActive()`가 원래 `CancellationException`을 보존한다. | PASS |
| 4. Concurrency·backpressure | blocking boundary와 race | `executeQuery`/`ResultSet.next` blocking과 실행 전 취소(`Order 328`)를 JDBC proxy로 검증하고, positive fetch size/channel bridge는 유지한다. 표준 JDBC가 `IDLE → IN_QUERY` 전환을 원자적으로 노출하지 않는 잔여 경합은 driver cancel 또는 positive `defaultQueryTimeout`/vendor API 조건으로 제한한다. | PASS / WATCH |
| 5. Test·fixture | deterministic integration proof | 실제 AGE Testcontainers 위 JDBC proxy로 cancel/close 카운트를 관찰하는 targeted 3개를 추가하고 AGE 전체 198개를 통과했다. driver unsupported path는 문서 제한으로 분리한다. | PASS |
| 6. 문서·운영 | KDoc/README/review/lesson/train | EN/KO AGE README, WIP, CHANGELOG, 설계·lesson·본 review에 driver 지원 한계와 lifecycle 소유권을 기록한다. | PASS |
| 7. 정적·회귀 | compile/test/detekt/static | AGE targeted/full test, Detekt, forbidden assertion scan, `git diff --check`와 exact PR metadata를 재검증한다. | PENDING |

## Findings와 처분

| 심각도 | 위치·문제 | 처분 |
|---|---|---|
| P1 (초기 finding) | statement 등록 직후 취소가 IDLE statement에 선점되는 경합 | `5a21d911`에서 실행 전 `ensureActive()`와 `statementExecuting` 경계를 분리하고 Order 328 회귀를 추가했다. 표준 JDBC의 마지막 `ensureActive()` 이후 `IDLE → IN_QUERY` handoff는 원자 보장이 없어 driver/timeout 조건부 계약으로 제한한다. | 수정 완료, residual WATCH |
| P2 | driver가 `Statement.cancel()`을 무시하거나 IDLE handoff를 놓치면 bounded cancellation을 보장할 수 없음 | public KDoc/EN/KO README에 driver capability, positive `defaultQueryTimeout`, vendor API 책임을 명시하고 일반 보장으로 확대하지 않음 |
| P2 | cancel 실패는 exactly-once guard를 소비하며 retry하지 않음 | `SQLException`을 `log.warn`으로 보존하고, 중복 cancel을 피하는 범위를 유지한다. 운영자는 driver timeout/vendor API를 함께 설정해야 한다. |
| P2 | connection pool 재대여와 원인 identity는 proxy counter/type으로만 간접 검증 | Exposed cleanup close와 원래 `CancellationException` 타입을 검증했으며, 실제 pool 재대여·cause identity는 별도 follow-up 범위로 남긴다. |
| P3 | `InternalCoroutinesApi` opt-in | private blocking 경계의 함수-level opt-in으로 제한하고 upgrade watch를 유지한다. |

## 검증 증거

- targeted cancellation: `executeQuery` blocking, `ResultSet.next` blocking,
  pre-execution cancellation을 포함한 `3/3`, 모두 `BUILD SUCCESSFUL`.
- blocking 두 테스트는 active statement `cancel=1`, statement `close=1`을
  검증하고, 두 번째 테스트는 `ResultSet close=1`, 첫 번째 테스트는 원래
  `CancellationException` 전파를 검증한다. 실행 전 테스트는 `executeQuery=0`,
  `cancel=0`, statement `close=1`을 검증한다.
- AGE full test `198/198`, Detekt, forbidden assertion scan, `git diff --check`는
  최신 candidate에서 통과했다. PR hosted evidence는 GitHub live metadata로
  대조할 때까지 PENDING이다.

## 결론

- 현재 구현 판정: `PASS / WATCH`.
- P0 merge blocker: 현재 없음. 초기 P1 등록 경합은 보정했지만, 표준 JDBC의
  마지막 `ensureActive()` 이후 driver state handoff는 조건부 bounded 계약으로
  남긴다.
- WATCH: 실제 driver의 `Statement.cancel()`/positive query timeout 지원 여부와
  hosted exact-head evidence는 PR 단계에서 확인해야 한다.
- 최종 train merge는 별도 마지막 승인 단계에서만 수행한다.

## SPW writer gate

- **SPW-01 — Lock audience, purpose, evidence: PASS.** AGE 유지보수자와
  reviewer를 대상으로 blocking cancellation contract를 고정했다.
- **SPW-02 — Artifact contract: PASS.** 범위, 설계, 7-Tier, findings, 검증,
  verdict, driver 제한을 포함한다.
- **SPW-03 — Korean technical register: PASS.** 설명과 판단은 한국어이고
  코드 토큰·명령·예외명은 원문을 보존한다.
- **SPW-04 — Technical traceability: PASS.** #552/#574 base, source symbol,
  test double와 cleanup 경계를 연결한다.
- **SPW-05 — Read-back: PENDING.** 최종 PR head, checks, review threads를
  생성 후 GitHub live metadata로 재대조한다.

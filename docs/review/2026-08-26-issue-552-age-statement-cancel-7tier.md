# #552 AGE JDBC statement 취소 수명주기 7-Tier 검토

## 검토 범위와 기준

- 대상 이슈: [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)
- 대상 모듈: `graph-age`
- 기준 base: PR [#574](https://github.com/bluetape4k/bluetape4k-graph/pull/574)의
  live exact head `130532a2c2f0be2e9c87572ed6876bbb688afa06`.
- 대상 PR: [#575](https://github.com/bluetape4k/bluetape4k-graph/pull/575).
  구현 관찰 head는 `35a9bef41daf5176a16695ee48cb15d7584e5344`이고, 이후
  read-back docs commit은 `093f2a7cd09a3191965a869acc34ff5883d92379`이다. 최종
  live head는 GitHub metadata를 권위로 삼는다.
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
| 4. Concurrency·backpressure | blocking boundary와 race | `executeQuery`/`ResultSet.next` latch double에서 cancellation callback과 statement 등록 순서를 검증하고, positive fetch size/channel bridge는 유지한다. | PASS |
| 5. Test·fixture | deterministic integration proof | 실제 AGE Testcontainers 위 JDBC proxy로 cancel/close 카운트를 관찰하는 두 테스트를 추가한다. driver unsupported path는 문서 제한으로 분리한다. | PASS |
| 6. 문서·운영 | KDoc/README/review/lesson/train | EN/KO AGE README, WIP, CHANGELOG, 설계·lesson·본 review에 driver 지원 한계와 lifecycle 소유권을 기록한다. | PASS |
| 7. 정적·회귀 | compile/test/detekt/static | AGE targeted/full test, Detekt, forbidden assertion scan, `git diff --check`와 exact PR metadata를 재검증한다. | PENDING |

## Findings와 처분

| 심각도 | 위치·문제 | 처분 |
|---|---|---|
| P0/P1 | 현재 식별 없음 | targeted/full 검증과 hosted exact-head review에서 재확인 |
| P2 | driver가 `Statement.cancel()`을 무시하면 bounded cancellation을 보장할 수 없음 | public KDoc/EN/KO README에 driver capability와 timeout 책임을 명시하고 일반 보장으로 확대하지 않음 |
| P2 | `onCancelling`은 `InternalCoroutinesApi` opt-in이 필요함 | blocking JDBC 경계의 최소 범위에만 함수-level opt-in을 두고, ABI 변경 없이 유지 |
| P3 | 없음 | — |

## 검증 증거

- targeted cancellation: `executeQuery` blocking `1/1`, `ResultSet.next` blocking
  `1/1`, 모두 `BUILD SUCCESSFUL`.
- 두 테스트 모두 active statement `cancel=1`, statement `close=1`을 검증하고,
  두 번째 테스트는 `ResultSet close=1`, 첫 번째 테스트는 원래
  `CancellationException` 전파를 검증한다.
- AGE full test, Detekt, forbidden assertion scan, `git diff --check`와 PR
  hosted evidence는 구현 완료 후 이 문서와 PR body에 fresh result를 기록한다.

## 결론

- 현재 구현 판정: `PASS / WATCH`.
- P0/P1 merge blocker: 현재 없음.
- WATCH: 실제 driver의 `Statement.cancel()` 지원 여부와 hosted exact-head
  evidence는 PR 단계에서 확인해야 한다.
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

# #550 AGE streaming fetch size·retry 장애 주입 7-Tier 검토

## 검토 범위와 기준

- 대상 이슈: [#550](https://github.com/bluetape4k/bluetape4k-graph/issues/550)
- 대상 모듈: `graph/graph-age`
- 기준 ref: `fix/issue-550-age-streaming-fetch-retry` exact HEAD `1b59e6df427de509127efe89c6cc0d2d5895a161` (PR #573; base는 #535 PR #572 exact head `03513d1a68bddda34105e2f48cb67bd0eb4ee0e6`)
- 검토 범위: #535가 구현한 direct `Flow`의 실제 `PreparedStatement.fetchSize` 전달, 비양수 fallback `100`, late `SQLException` 단일 시도·prefix 중복 방지를 AGE Testcontainers와 JDBC proxy에서 관찰한다.
- 변경 범위: production API·ABI 변경 없이 `AgeGraphSuspendOperationsTest`에 `DataSource`/`Connection`/`PreparedStatement`/`ResultSet` proxy fault injection만 추가한다.
- 후속 경계: `executeQuery()`/`ResultSet.next()` 자체 stall과 `Statement.cancel()` 연동은 [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552), nested `Flow` escape는 [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551)에서 다룬다.

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
|---|---|---|---|
| 1. 계약·호환성 | public API/ABI, 범위, base | 테스트 전용 변경이며 `AgeGraphSuspendOperations` public constructor/interface/ABI를 수정하지 않는다. PR #572 exact head `03513d1a` 위에만 적층한다. | PASS |
| 2. Kotlin 패턴·API | null/error/coroutine/assertions | `runSuspendIO`, 실제 `DataSource` 위임, `assertFailsWith<Exception>`, `SQLException` cause 확인을 사용한다. 금지된 `assertThrows`/`shouldThrow`는 없다. | PASS |
| 3. Lifecycle·취소 | statement/result set/transaction | proxy는 실제 JDBC 객체를 위임하고 `ResultSet.next()`의 두 번째 호출에서만 late 오류를 주입한다. production의 `ResultSet.use`와 transaction close를 그대로 통과한다. | PASS |
| 4. Concurrency·backpressure | fetch/retry/prefix | configured `8`, fallback `100`, positive fetch statement 횟수와 emitted prefix 1건·streaming attempt 1회를 직접 관찰한다. driver 내부 stall은 #552로 제한한다. | PASS |
| 5. Test·fixture | deterministic integration | AGE Testcontainers에서 32개 targeted와 194개 graph-age 전체 테스트를 순차 실행했고, 두 행 fixture로 late failure를 재현한다. | PASS |
| 6. 문서·운영 | README/review/lesson/train | #535 README EN/KO의 fetch/retry 경계를 재사용하고 본 review·lesson·CHANGELOG·WIP에 fault-injection 증거와 잔여 범위를 기록한다. | PASS |
| 7. 정적·회귀 | compile/detekt/diff | `compileKotlin`, `compileTestKotlin`, `detekt`, `git diff --check`가 통과한다. proxy helper는 test source에만 존재한다. | PASS |

## 독립 검토 종합

### Architecture lane

- 판정: `PASS`, Architectural Status `WATCH`
- 확인: 실제 AGE connection을 감싼 proxy가 `setFetchSize(8)`와 fallback `setFetchSize(100)`을 기록하고, 두 번째 `ResultSet.next()`에서 `SQLException`을 발생시킨다. Exposed streaming transaction의 `maxAttempts=1`이 재시도 없이 종료되며 collector가 받은 첫 prefix는 한 번만 남는다.
- WATCH: `Statement.cancel()`이 필요한 driver stall은 #552의 별도 lifecycle 계약이며 이 PR이 prompt cancellation을 주장하지 않는다.

### Code lane

- 판정: `PASS`, recommendation `PASS / WATCH`
- 확인 범위: test proxy의 delegate invocation/예외 원인 보존, bluetape assertions, configured/fallback fetch size, late failure attempt count, full module regression.
- P0/P1: 없음. P2는 #552 stall cancellation과 #551 nested Flow의 기존 후속 범위뿐이다.

## Findings와 처분

| 심각도 | 위치·문제 | 처분 |
|---|---|---|
| P0/P1 | 없음 | 수정 불필요 |
| P2 | 정상 AGE 경로만으로는 실제 statement fetch size와 retry 억제를 증명할 수 없음 | `ProbingDataSource`와 JDBC proxy로 `8`/`100`, late `SQLException`, emitted prefix `1`, attempt `1`을 관찰하는 회귀 테스트 추가 |
| P2 | `executeQuery()`/`ResultSet.next()` 내부 stall 취소는 여전히 미검증 | #552로 유지하고 본 PR의 범위를 넘지 않음 |
| P2 | transaction 밖 nested `Flow` escape | #551로 유지하고 본 PR의 범위를 넘지 않음 |

## 검증 증거

- targeted: `./gradlew :bluetape4k-graph-age:test --tests 'io.bluetape4k.graph.age.AgeGraphSuspendOperationsTest' --no-build-cache --no-daemon --console=plain` → `32/32`, `BUILD SUCCESSFUL`.
- module: `./gradlew :bluetape4k-graph-age:test --no-build-cache --no-daemon --console=plain` → `194/194`, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- static/build: `:bluetape4k-graph-age:detekt`, `compileKotlin`, `compileTestKotlin` → `BUILD SUCCESSFUL`.
- diff/terms: `git diff --check` PASS; new test source contains `io.bluetape4k.assertions.assertFailsWith` and no forbidden assertion.
- lifecycle note: AGE Testcontainers는 기존 graph-age 모듈 전체 테스트와 같은 단일 모듈에서 순차 실행했으며 별도 retry 없이 통과했다.

## 결론

- 최종 7-Tier verdict: `PASS`
- Architectural Status: `WATCH` (비차단 #551/#552 후속 계약)
- Code lane recommendation: `PASS / WATCH`
- merge blocker: 없음
- #550은 #535 PR #572 exact head 위에 독립 PR로 열고, #551/#552는 같은 train의 후속 slice로 유지한다.

## SPW writer gate

- **SPW-01 — Lock audience, purpose, evidence: PASS.** graph-age 유지보수자와 reviewer를 대상으로 실제 JDBC 경계의 fault-injection evidence를 기록했다.
- **SPW-02 — Artifact contract: PASS.** 범위, 7-Tier, 독립 검토, findings/disposition, validation, verdict, 후속 경계를 포함한다.
- **SPW-03 — Korean technical register: PASS.** code token과 명령은 보존하고 현재 구현·관찰·미범위를 분리했다.
- **SPW-04 — Technical traceability: PASS.** #535 base PR, source test seam, 32/194 test evidence, #551/#552 후속 issue를 연결했다.
- **SPW-05 — Read-back: PASS.** exact head와 검증 수치를 PR #573 live metadata에 다시 대조했다.

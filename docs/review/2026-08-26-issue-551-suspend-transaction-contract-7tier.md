# #551 suspendTransaction 중첩 Flow 결과 계약 7-Tier 검토

## 검토 범위와 기준

- 대상 이슈: [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551)
- 대상 모듈: `graph-core`, `graph-age`, `graph-neo4j`, `graph-memgraph`,
  `graph-tinkerpop`
- 기준 base: PR [#573](https://github.com/bluetape4k/bluetape4k-graph/pull/573)의
  live exact head `186ea8af18192d8fe1e8024bc78cc80b7f235bc1`.
- 현재 review ref: PR [#574](https://github.com/bluetape4k/bluetape4k-graph/pull/574)의
  live exact head를 GitHub metadata에서 authoritative receipt로 재확인한다. 최초
  review 관찰 head는 `b9decdc36f68dd005eb396465fa58e751a94aad5`였으며, 이 문서는
  self-referential SHA를 기준으로 삼지 않는다. hosted checks와 review threads는
  현재 대기 중이다.
- 검토 결정: 최상위 `Flow`는 commit 전에 materialize하고, 표준 컨테이너 내부의
  중첩 `Flow`는 `IllegalArgumentException`으로 거부한다. 임의 사용자 wrapper는
  reflection 없이 호출자 책임으로 둔다.
- 후속 경계: [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)의
  driver stall cancellation은 본 변경에 포함하지 않는다.

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
|---|---|---|---|
| 1. 계약·호환성 | public API/ABI와 stack base | `suspendTransaction` 시그니처는 유지하고, 네 backend가 graph-core helper를 공유한다. PR #573 exact base 위의 additive helper다. | PASS |
| 2. Kotlin 패턴·API | null/error/coroutine/assertions | `Flow.toList()`와 재수집 가능한 `asFlow()`를 사용하고, 표준 컨테이너는 identity visited set으로 재귀 검사한다. 새 예외 테스트는 `io.bluetape4k.assertions.assertFailsWith`를 사용한다. | PASS |
| 3. Lifecycle·취소 | commit/rollback와 resource escape | 모든 backend가 commit 전에 helper를 호출한다. helper 예외는 기존 transaction catch/rollback 경계로 전달되고, 중첩 cursor가 transaction 밖으로 escape하지 않는다. | PASS |
| 4. Concurrency·backpressure | Flow materialization 경계 | 최상위 `Flow`를 commit 전에 수집해 backend cursor 수명을 닫고, 중첩 Flow는 block 안 materialization을 요구한다. driver 내부 stall 취소는 #552로 제한한다. | PASS / WATCH |
| 5. Test·fixture | 공통·backend 회귀 | core 표준 컨테이너 계약과 AGE·Neo4j·Memgraph·TinkerPop rollback 테스트를 추가했다. backend Testcontainers는 순차 실행한다. | PASS |
| 6. 문서·운영 | KDoc/README/review/lesson/train | graph-core EN/KO README, KDoc, WIP, CHANGELOG, 설계·lesson·본 review에 migration과 제한을 기록한다. | PASS |
| 7. 정적·회귀 | compile/detekt/forbidden/diff | 다섯 모듈 Detekt, 전체 테스트, forbidden assertion scan, `git diff --check`를 검증한다. | PASS |

## 독립 검토 종합

### Architecture lane

- 1차 독립 판정: `PASS / WATCH`; 문서 범위 보완 전 P1을 식별했고, public KDoc와
  graph-core EN/KO README에 `Sequence`·custom wrapper 비검사 및 호출자 책임을
  명시한 뒤 재확인을 요청했다.
- 확인 포인트는 helper의 공통 경계, 네 backend commit 순서, rollback 전파,
  additive API/ABI, #552와의 scope separation이다.

### Code lane

- 독립 판정: `PASS / WATCH`; P0/P1 `0`, P2 `2`, P3 `2`로 보고되었고 코드 수준
  merge blocker는 없다.
- 확인 포인트는 Kotlin type inference가 transaction 결과를 `Unit`으로 소거하지
  않는지, bluetape assertions 사용, forbidden assertion 부재, 표준 컨테이너
  recursion과 오류 메시지 traceability다.

## Findings와 처분

| 심각도 | 위치·문제 | 처분 |
|---|---|---|
| P0/P1 | 현재 식별 없음 | 독립 review와 hosted exact-head 검증에서 재확인 |
| P2 | `Sequence`와 임의 사용자 wrapper 내부의 `Flow`는 reflection/iteration 없이 탐지하지 않음 | public KDoc·EN/KO README에 비검사 범위와 호출자 materialization 책임을 명시 |
| P2 | driver 내부 query stall 및 `Statement.cancel()` 미검증 | #552 후속 범위로 유지하며 본 PR에서 확대 주장하지 않음 |
| P3 | 없음 | — |

## 검증 증거

- graph-core 전체: `355` tests, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- graph-tinkerpop 전체: `119` tests, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- graph-neo4j 전체: `132` tests, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- graph-memgraph 전체: `124` tests, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- graph-age 전체: `195` tests, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- targeted contract: core `4/4`, TinkerPop `31/31`, Neo4j `33/33`, Memgraph
  `34/34`, AGE suspend class `BUILD SUCCESSFUL`; backend Testcontainers는
  AGE → Neo4j → Memgraph 순차 실행했다.
- static: 다섯 모듈 `detekt` 성공, 금지 assertion `0`, `git diff --check` 성공.

## 결론

- 구현 판정: `PASS / WATCH`.
- P0/P1 merge blocker: 현재 없음.
- WATCH: custom wrapper 책임과 #552 driver stall cancellation은 명시적 후속
  범위다.
- PR #574 exact head는 live metadata와 대조했으며, hosted checks와 review threads
  완료 후 SPW-05를 갱신한다.

## SPW writer gate

- **SPW-01 — Lock audience, purpose, evidence: PASS.** graph-core/backend
  유지보수자와 reviewer를 대상으로 transaction 결과 경계와 검증 증거를 고정했다.
- **SPW-02 — Artifact contract: PASS.** 범위, 설계, 7-Tier, findings, 검증,
  verdict, 후속 경계를 포함한다.
- **SPW-03 — Korean technical register: PASS.** 코드 토큰·명령·예외명은 원문을
  보존하고 설명과 판단은 한국어로 작성했다.
- **SPW-04 — Technical traceability: PASS.** #550/#573 base, source modules,
  test counts, #552 후속 범위를 연결했다.
- **SPW-05 — Read-back: PENDING.** 최종 PR head와 hosted evidence를 생성 후
  GitHub live metadata로 재대조한다.

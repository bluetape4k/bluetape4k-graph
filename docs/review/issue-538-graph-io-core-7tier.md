# #538 graph-io-core 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#538](https://github.com/bluetape4k/bluetape4k-graph/issues/538)
- 대상: `graph-io-core` workflow state transition과 sync/suspend batch writer 입력 계약
- stacked base: PR [#575](https://github.com/bluetape4k/bluetape4k-graph/pull/575)의
  live exact head `941c822e40f670ae8d856fad893f0922ae5d8a0d`
- branch: `fix/issue-538-graph-io-core-stacked`
- exact HEAD: `6e648846f4b6079f07a560e101a1978f18c5ac16`
- 판정: **PASS / WATCH** (P0/P1 blocker 없음)
- 외부 상태: PR 생성 전 로컬 검증 단계이며 merge와 이슈 close를 수행하지 않음

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·범위·기존 계약 확인 | live #538, graph-io-core source/history, PR #575 exact base, `bluetape-workflow` Type C/Bug Fix 분류 | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 대조 | additive default `GraphImportJobStateStore.update`, `requirePositiveNumber("batchSize")`, sync/suspend parity, public KDoc 및 README en/ko | PASS |
| SPW-03 | lifecycle·동시성 검증 | `load → transform → jobId 검증 → save` atomic monitor; 두 workflow race에서 1 success + 1 `IllegalArgumentException` | PASS |
| SPW-04 | 테스트·정적 검증 | RED 재현 후 targeted 11/11, full 142/142, detekt, Kotlin compile/test-compile, 금지 assertion scan, `git diff --check` | PASS |
| SPW-05 | 문서·운영 증거 | design/plan, 이 리뷰와 lesson, 후속 issues #553/#554/#555, stacked base/head read-back | PASS |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. API/ABI | 기존 public signature·생성자·report `serialVersionUID`를 깨뜨리지 않는가 | PASS. `update`만 JVM default method로 additive 추가됨 |
| 2. Kotlin/Bluetape 패턴 | null safety, 불변성, 공용 helper, sync/suspend 대칭을 지키는가 | PASS. `requirePositiveNumber`와 `io.bluetape4k.assertions.assertFailsWith` 사용 |
| 3. 상태·동시성 | stale read와 중복 전이를 차단하는가 | PASS. 동일 store monitor 안에서 최신 state를 재검증하고 저장 |
| 4. 오류·계약 | 실패가 조용히 삼켜지지 않고 입력 계약이 일관적인가 | PASS. jobId mismatch와 비양수 batchSize를 즉시 실패 |
| 5. 테스트 | 회귀·경계·병행 경로가 재현 가능한가 | PASS. 실제 두 번째 task 시작 barrier와 sync/suspend invalid input 회귀 포함 |
| 6. 문서·호환성 | durable override 한계와 reader-facing 문장이 일치하는가 | PASS. KDoc 및 README en/ko에 pure/retry-safe transform, CAS/transaction override를 명시 |
| 7. 운영·유지보수 | 검증 receipt와 후속 위험이 추적 가능한가 | WATCH. 비차단 P2/P3를 후속 issue로 분리하고 merge gate는 PASS |

## 독립 리뷰 합의

Architecture lane과 code-review lane이 exact HEAD를 별도로 읽었다.

- 공통: P0=0, P1=0, race barrier·transform retry-safe 문서·writer validation은 통과
- P2: 상태 전이 시 기존 `sources`/`elapsed`/`checkpoint` 보존 회귀가 없음 → [#553](https://github.com/bluetape4k/bluetape4k-graph/issues/553)
- P2: durable/CAS store의 contention·retry·jobId invariant 공통 TCK가 없음 → [#554](https://github.com/bluetape4k/bluetape4k-graph/issues/554)
- P3: 기본 monitor가 store 전체 job을 직렬화 → [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)
- P3: `jobId` mismatch 방어의 직접 회귀 테스트가 없음 → #554 TCK 범위에 포함

위 항목은 현재 in-memory production store와 #538 수용 기준을 막지 않는
비차단 관찰이다. report payload 손실은 기존 dormant 동작이며, durable store는
문서화된 override 경계 밖이므로 각각 별도 계약으로 추적한다.

## 검증 영수증

- `GraphIoBatchWriterTest`와 `GraphImportWorkflowTest` targeted: 11/11 PASS
- `:bluetape4k-graph-io-core:test`: `SUCCESS: Executed 142 tests`, 0 failure/error/skipped
- `:bluetape4k-graph-io-core:detekt`: PASS
- `compileKotlin`, `compileTestKotlin`: PASS
- 금지 assertion/fallback scan: clean (`assertThrows`, `kotlin.test.assertFailsWith`, `shouldThrow`, `runCatching`, `invoking {` 없음)
- `git diff --check 941c822e40f670ae8d856fad893f0922ae5d8a0d..HEAD`: PASS

초기 병렬 `--rerun-tasks` 시도에서 일시적인 Companion class loading/report
lifecycle 오류가 있었으나, 원인을 classpath/build-output race로 분리한 뒤
`cleanTest` 후 순차 targeted·detekt·full을 재실행해 모두 통과했다.

## 최종 결론

`#538` 구현은 PR #575 exact head 위에서 Kotlin pattern, Bluetape
helper/assertions, API/ABI, 동시성 및 검증 계약을 충족한다. **PR readiness:
PASS / Architecture status: WATCH**. PR 생성 후 hosted exact-head checks와
리뷰 read-back을 추가하고, 최종 train merge는 마지막 승인 단계에서만 수행한다.
후속 이슈가 해결되기 전에도 P0/P1 기준의 병합 차단 사유는 없다.

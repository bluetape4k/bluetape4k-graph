# #542 graph-core Virtual Thread helper owner 7-Tier 코드 리뷰

## 판정

- 이슈: [#542](https://github.com/bluetape4k/bluetape4k-graph/issues/542)
- stacked base: #561 PR #586 exact head `295ec47cbc4dc76028b2f4bc72425c37bef9c9d3`
- 범위: graph-core local `virtualFutureOfNullable` source 제거, official helper TCK,
  EN/KO README migration note
- 판정: **PASS / WATCH**
- 심각도: P0 0, P1 0, P2 2, P3 1

graph-core local owner 제거는 완료하지만 upstream 두 artifact의 split-package와
외부 precompiled consumer ABI는 각각 #563과 #562에서 후속 검증한다. 이 review는
PR merge와 issue close를 승인하지 않는다.

## 수용 기준 추적

| 기준 | 근거 | 상태 |
| --- | --- | --- |
| 공식 Bluetape helper 사용 | graph-core dependency와 adapter import | PASS |
| graph-local generated owner 제거 | `CompletableFutureNullableSupport.kt` 삭제, ownership TCK | PASS |
| nullable future 실행 계약 | `VirtualThreadOfficialUtilityTest` | PASS |
| package split와 generated owner migration 문서화 | graph-core EN/KO README, spec | PASS |
| 새 dependency 없이 catalog/BOM 유지 | `graph-core/build.gradle.kts` 변경 없음 | PASS |

## 7-Tier 결과

| Tier | 검토 내용 | 판정 및 잔여 위험 |
| --- | --- | --- |
| 1. Correctness | official `virtualFutureOfNullable { null }` execution과 local owner 부재 | PASS. RED에서 local owner 로드를 관찰하고 삭제 후 GREEN이 됐다. |
| 2. API/ABI | Kotlin source import와 adapter signature 유지 | PASS/WATCH. generated `CompletableFutureNullableSupportKt` 직접 consumer는 재컴파일이 필요하며 #562에서 외부 TCK로 확인한다. |
| 3. Kotlin/Bluetape pattern | official dependency reuse, immutable source scope, Bluetape assertions | PASS. 새 executor/helper/dependency를 만들지 않았다. |
| 4. Reliability/Concurrency | helper가 shared Bluetape virtual-thread executor에서 실행 | PASS. backend cancellation은 이번 ownership slice가 다루지 않는다. |
| 5. Security/Resource | duplicate class/package ownership 감소, no shading | PASS/WATCH. upstream split-package module boundary는 #563 후속 범위다. |
| 6. Tests/Observability | ownership Class.forName guard, nullable result, full graph-core verification | PASS. external precompiled consumer와 codeSource는 별도 issue다. |
| 7. Documentation/Maintainability | EN/KO README, spec/plan, review, lesson, WIP/CHANGELOG | PASS. migration과 후속 issue 경계를 명시했다. |

## 검증 영수증

- TDD RED: local `CompletableFutureNullableSupportKt`가 존재해
  `ClassNotFoundException` guard가 실패했다.
- TDD GREEN: local owner 부재, official owner 존재, nullable result 1개 TCK 통과.
- graph-core full test: 379개 통과(선행 #561의 378개에 ownership TCK 1개 추가).
- compileKotlin·Detekt·금지 assertion scan·`git diff --check`: 통과.
- clean output에는 graph-local `CompletableFutureNullableSupportKt`가 없고,
  공식 owner는 `CompletableFutureSupportKt`로 확인한다.
- hosted exact-head CI·Examples receipt는 PR 생성 후 최종 head에서 갱신한다.

## 후속 위험

- P2: generated top-level owner를 직접 참조한 Java/precompiled Kotlin consumer는
  공식 owner로 재컴파일해야 한다. #562에서 fixture와 `ProtectionDomain.codeSource`를
  검증한다.
- P2: `bluetape4k-core`와 `bluetape4k-virtualthread-api`가 같은 package를 나누는
  upstream split-package는 graph-core source 제거만으로 해결되지 않는다. #563에서
  `java --validate-modules`와 artifact ownership을 정리한다.
- P3: 향후 ecosystem helper를 추가할 때 catalog, resolved jar, generated owner,
  source migration을 같은 review에서 확인한다.

## 최종 결론

graph-core는 중복 nullable helper를 제거하고 공식 Bluetape4k utility를 사용한다.
**PR readiness: PASS / Architecture status: WATCH**. 외부 ABI와 upstream module 경계는
후속 issue로 남기며, 전체 stacked train merge는 마지막 승인 단계에서만 수행한다.

## SPW-01 Source ledger

| 출처 | 사용 목적 |
| --- | --- |
| #542 live issue | duplicate owner와 acceptance |
| #561 PR #586 | stacked base와 최신 Virtual Thread surface |
| `graph-core/build.gradle.kts` | official core/virtualthread-api dependency |
| `CompletableFutureSupport.kt` | official helper owner |
| `VirtualThreadOfficialUtilityTest.kt` | local/official owner TCK |
| graph-core EN/KO README | migration와 split-package 경계 |

## SPW-02~05

- SPW-02 review 구조: 범위, 7-Tier, 근거, 검증, 잔여 위험을 포함했다.
- SPW-03 한국어 기술 문체: reader-facing prose는 한국어, API·identifier·URL은 원문을 유지했다.
- SPW-04 사실 추적성: source diff, TDD RED/GREEN, full test/compile/detekt와 문서를 연결했다.
- SPW-05 read-back: 표·링크·README locale을 다시 읽고 `git diff --check`를 실행한다.

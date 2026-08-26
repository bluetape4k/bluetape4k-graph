# #562 generated owner ABI migration TCK 7-Tier 코드 리뷰

## 판정

- 이슈: [#562](https://github.com/bluetape4k/bluetape4k-graph/issues/562)
- 선행 PR: [#587](https://github.com/bluetape4k/bluetape4k-graph/pull/587)
- stacked base: `0c859bc135c1ca68efcd36690427ccb2863773b1`
- 구현 범위: graph-core 외부 consumer ABI fixture, official owner code source,
  EN/KO migration 안내
- 판정: **PASS / WATCH**
- 심각도: P0 0, P1 0, P2 1, P3 1

legacy generated owner 직접 호출은 의도적으로 재컴파일 경계가 된다(P2). JDK
compiler와 임시 classloader에 의존하는 test harness는 테스트 환경 차이를
관찰해야 하는 P3로 남긴다.

## 수용 기준 추적

| 기준 | 근거 | 판정 |
| --- | --- | --- |
| 이전 owner 직접 참조 fixture | `LegacyVirtualFutureConsumer`가 legacy owner method를 호출 | PASS |
| official owner 재컴파일 fixture | `MigratedVirtualFutureConsumer`가 `CompletableFutureSupportKt`를 호출 | PASS |
| 실패·migration 경로 | legacy runtime `NoClassDefFoundError`, migrated nullable future `null` | PASS |
| 실제 artifact owner | official class `ProtectionDomain.codeSource` 경로에 `bluetape4k-core` 포함 | PASS |
| source/ABI 차이 | legacy/migrated classfile constant-pool owner 비교 | PASS |
| Bluetape4k 사용과 catalog 보존 | Bluetape assertions, build/catalog diff 없음 | PASS |
| reader-facing migration 문서 | graph-core EN/KO README, CHANGELOG, WIP | PASS |

## 7-Tier 결과

| Tier | 검토 내용 | 판정 및 잔여 위험 |
| --- | --- | --- |
| 1. Correctness | legacy linkage failure와 migrated nullable future 실행 | PASS. 두 경로가 서로 다른 classloader/owner 조건에서 재현된다. |
| 2. API/ABI | generated owner만 달라지고 Kotlin source-level helper 의미는 유지 | PASS/WATCH. legacy direct Java/precompiled consumer는 재컴파일이 필요하다. |
| 3. Kotlin/Bluetape pattern | official dependency reuse, immutable fixture setup, Bluetape assertions | PASS. production dependency·shim을 추가하지 않았다. |
| 4. Reliability/Concurrency | official helper의 `CompletableFuture` completion과 isolated loader | PASS. backend cancellation이나 executor 정책은 이 TCK 범위가 아니다. |
| 5. Security/Resource | temporary output cleanup, runtime에 legacy owner 미포함 | PASS/WATCH. compiler/classloader 환경은 JDK 25 계약으로 제한한다. |
| 6. Tests/Observability | TDD RED→GREEN, code source, linkage cause, constant-pool owner 검사 | PASS. 외부 release repository matrix는 별도 검증하지 않는다. |
| 7. Documentation/Maintainability | EN/KO README, spec/plan, lesson, CHANGELOG, WIP, receipt | PASS. #563 split-package 경계를 혼동하지 않게 기록했다. |

## 검증 영수증

- TDD RED: fixture resource가 없는 초기 harness에서 2개 TCK가
  `ABI fixture resource not found`로 실패했다.
- TDD GREEN: `VirtualFutureOwnerAbiMigrationTckTest` 3/3 통과.
- legacy fixture: 제거된 owner 호출이 `NoClassDefFoundError`와
  `io/bluetape4k/concurrent/virtualthread/CompletableFutureNullableSupportKt`를
  보고했다.
- migrated fixture: official owner 재컴파일 후 nullable `CompletableFuture`가
  `null`로 완료됐다.
- official owner code source: `bluetape4k-core` artifact 경로 확인.
- graph-core 전체 test: 382/382 통과.
- `compileKotlin`, Detekt, 금지 assertion scan, `git diff --check`: 통과.
- hosted exact-head CI·Examples와 독립 review는 PR 생성 후 갱신한다.

## 후속 위험과 결론

- P2: 이전 generated owner를 직접 호출한 consumer는 공식 owner 기준으로
  재컴파일해야 한다. compatibility shim을 추가하지 않는다.
- P3: `JavaCompiler`가 없는 runtime은 지원하지 않는다. 프로젝트 toolchain이
  JDK 25이므로 JRE-only 실행을 성공 경로로 간주하지 않는다.
- `bluetape4k-core`와 `bluetape4k-virtualthread-api` split-package 및 module
  validation은 [#563](https://github.com/bluetape4k/bluetape4k-graph/issues/563)의
  후속 범위다.

**PR readiness: PASS / Architecture status: WATCH**. 전체 stacked train의
최종 승인 전까지 PR merge와 issue close는 보류한다.

## SPW-01 Source ledger

| 출처 | 사용 목적 |
| --- | --- |
| [#562](https://github.com/bluetape4k/bluetape4k-graph/issues/562) | ABI fixture와 migration 수용 기준 |
| [#587](https://github.com/bluetape4k/bluetape4k-graph/pull/587) | 정확한 stacked base와 owner 제거 사실 |
| `CompletableFutureSupport.kt` (bluetape4k core) | official generated owner와 signature |
| `VirtualFutureOwnerAbiMigrationTckTest` | compile/runtime isolation과 assertions |
| `src/test/resources/abi/virtual-future` | legacy/migrated Java fixture source |
| graph-core EN/KO README | reader-facing 재컴파일 및 split-package 안내 |

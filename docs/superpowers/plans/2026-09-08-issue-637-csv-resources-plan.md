# #637 CSV classpath 공통화 구현 계획

> 실행 지침: `$executing-plans`로 아래 순서와 확인란을 추적한다. 구현 소유자는 리더이며 테스트 실행은 모든 #641 작업과 직렬화한다.

목표: 8개 loader의 classpath 탐색과 resource close 코드를 기존 행동을 보존하면서 공개 CSV helper로 옮긴다.

구조: vertices/edges를 두 nested use로 소유한다. suspend는 callerContext/TCCL을 먼저 캡처하고 outer IO use 안의 callback만 callerContext로 실행한다. 기술: Kotlin 2.4, JDK 25, kotlinx.coroutines, 기존 graph-io-csv. 새 의존성은 없다.

기준 설계: `../specs/2026-09-08-issue-637-csv-resources-design.md`. 여섯 spec 관점과 통합 검토 P0/P1=0. 사용자 #641 개별 PR 지시 범위의 내부 공통화와 기존 계약 보존이다.

## 1. 공통 API 테스트와 최소 표면 — AC1~3

- [x] `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvGraphClasspathResourcesTest.kt` 추가. close 횟수/순서/예외를 기록하는 실제 InputStream subclass와 Map ClassLoader fixture 사용. 외부 DB 불필요.
- [x] `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphClasspathResources.kt`에 두 generic callback 함수의 최소 throw stub만 추가하여 컴파일 가능한 API 표면을 제공한다. `fallbackClassLoader: ClassLoader?`, 기본 누락 메시지 함수, sync/suspend block과 반환 T를 갖는다.
- [x] `./gradlew :bluetape4k-graph-io-csv:test --tests '*.CsvGraphClasspathResourcesTest' --no-build-cache`로 새 동작 미구현의 행동 실패를 확인한다. 컴파일 실패는 RED 증거로 사용하지 않는다.

테스트 표: TCCL 우선/fallback/null/null; 상대 경로와 선행 slash 누락; vertex 누락; edge open 실패 시 vertex 1회 close; 정상 반환값과 closeInput=false; callback 실패+edge/vertex close 실패의 primary/suppressed 순서; edge-open failure+vertex-close failure; suspend callback dispatcher 보존; 실제 cancelAndJoin 후 두 stream close; 취소 예외+close 실패 보존; callback 반환 후 source 소비는 지원하지 않는다는 사용 금지 예시. TCCL은 finally에서 복원한다. assertFailsWith/runSuspendIO 등 기존 bluetape4k helper를 사용한다.

취소 테스트는 callback 진입 후 CompletableDeferred 신호를 보내고 awaitCancellation()으로 대기한다. 테스트가 신호를 await한 후 식별 가능한 CancellationException으로 cancel하며 invokeOnCompletion으로 terminal cause를 캡처한다. cancelAndJoin 후 primary identity, edge/vertex suppressed 순서, close 각 1회를 함께 검증한다. 전용 single-thread caller dispatcher에서 callback thread를 검증하고 loader lookup/open/close thread도 기록하여 caller thread 밖인지 확인한다. 모든 대기는 withTimeout으로 제한한다.

## 2. 최소 구현 — AC1~3

- [x] resolver는 import당 두 resource만 열며 TCCL 우선, nullable fallback 순서다. byte/record 처리 loop에 lookup을 추가하지 않는다.
- [x] 두 InputStreamSource는 closeInput=false. sync는 nested use, suspend는 설계의 outer IO nested use + inner withContext(callerContext)를 그대로 사용한다. cleanup에는 추가 suspend 지점이 없다. Kotlin use가 primary/suppressed를 보존한다.
- [x] 같은 targeted 명령 GREEN 후 `./gradlew :bluetape4k-graph-io-csv:cleanTest :bluetape4k-graph-io-csv:test :bluetape4k-graph-io-csv:detekt --no-build-cache` 실행. 실패 시 이 단계로 복귀. Kotlin patterns의 취소·소유권·validation·KDoc 규칙 준수.

## 3. 8개 호출자 이전 — AC4

- [x] 각 `examples/<module>/src/main/kotlin/io/bluetape4k/graph/examples/<domain>/io/<Loader>.kt`의 private withImportSource/withImportSourceSuspending/toSource/resourceStreamOrThrow만 제거하고 공개 helper로 호출을 바꾼다. 각 caller는 `fallbackClassLoader = <Loader>::class.java.classLoader`를 명시한다. 표의 8개 module 각각에 null TCCL sync/suspend import 테스트를 추가하여 각 loader fallback 연결을 검증한다. 기존 `importCsv` / `importCsvSuspending` API, default resource 이름, options, "Sample dataset resource not found: $it" 메시지는 유지한다.

| module | domain | Loader |
|---|---|---|
| fraud-detection-examples | fraud | FraudDetectionSampleDatasetLoader |
| knowledge-graph-examples | knowledge | KnowledgeGraphSampleDatasetLoader |
| recommendation-examples | recommendation | RecommendationSampleDatasetLoader |
| observability-graph-examples | observability | ObservabilitySampleDatasetLoader |
| supply-chain-graph-examples | supplychain | SupplyChainSampleDatasetLoader |
| data-lineage-examples | datalineage | DataLineageSampleDatasetLoader |
| network-topology-examples | networktopology | NetworkTopologySampleDatasetLoader |
| security-attack-path-examples | securityattack | SecurityAttackPathSampleDatasetLoader |

- [x] 실제 package/path는 rg --files의 현재 파일과 대조한 후 적용한다. 기존 public declaration을 git show 기준과 비교하고 변경된 source에서 helper 이전 외 차이가 없는지 확인한다.
- [x] 단일 직렬 Gradle invocation으로 `:fraud-detection-examples:test :knowledge-graph-examples:test :recommendation-examples:test :observability-graph-examples:test :supply-chain-graph-examples:test :data-lineage-examples:test :network-topology-examples:test :security-attack-path-examples:test --no-parallel --max-workers=1` 실행. 각 모듈의 기존 backend/sync/suspend sample 결과를 유지한다. examples에는 detekt task가 없으므로 testClasses compile과 적용 가능한 CSV detekt로 진단한다.

## 4. 문서·호환성·리뷰 — AC5~6

- [x] 새 공개 API의 한국어 KDoc, CSV README.md/README.ko.md에 nullable fallback, trusted configuration, ClassLoader 상대 경로, block 내 소비 완료와 lazy/async escape 금지, 반환값/예외/소유권을 기록한다. 8개 예제 README.md/README.ko.md는 공개 importCsv/importCsvSuspending·default resource·옵션이 유지되므로 변경 N/A이며 각 module 경로의 기존 예제 호출을 대조한다. CSV module README 양언어·CHANGELOG·WIP·lesson을 갱신한다. SPW-01~05와 source-to-doc readback 수행.
- [x] `javap -public`으로 새 helper JVM descriptors와 기존 loader descriptors를 기록한다. 기준 git source의 공개 함수 signature/기본값과 현재 선언을 대조한다. 기존 API 제거 없음, 새 helper만 추가됨을 확인한다. 기존 artifact를 삭제·재게시하지 않는다.
- [x] module 추가 없음으로 settings/BOM/catalog/Kover 등록 변경은 N/A. CI는 기존 `.github/workflows/ci.yml` graph-io 테스트와 `.github/workflows/examples.yml`의 `Build / All Examples (Testcontainers)` 및 `Examples Status`가 변경 경로를 포함하는지 확인한다. workflow/Nightly를 변경하지 않으므로 dispatch gate는 N/A.
- [x] spec/plan verifier 및 여섯 pre-PR 관점과 리더 통합 검토에서 P0/P1을 해소한다. docs/lessons에 시행착오와 규칙을 커밋한다. GNO는 worktree 제외 설정을 보존하고 merge 후 canonical checkout에서 lesson 검색 반영한다.

ABI 명령: `./gradlew :bluetape4k-graph-io-csv:jar` 후 `javap -public -classpath graph-io/csv/build/classes/kotlin/main io.bluetape4k.graph.io.csv.CsvGraphClasspathResourcesKt`. 각 loader는 해당 `examples/<module>/build/classes/kotlin/main` classpath와 표의 FQCN을 사용한다. 선언/기본값 비교 기준은 `git show 156097ebbc9e856e69349133b462a5a86a5ed58f:<loader-path>`이며 공개 fun 선언에서 private helper 부분을 제외해 현재와 diff한다. build.gradle.kts/buildSrc에는 별도 ABI task 등록이 없어 JVM descriptors와 Kotlin 선언 비교를 분리한다.

운영 검증의 담당자는 모두 #641 리더다: 로컬 테스트 직렬화, CI 진단, metadata readback, compatibility, rollback 및 최종 보류 판정. CI 실패는 해당 수정으로 복귀하고 새 의존성/게시/머지는 현재 작업 범위 밖이다.

| 확인 | 현재 근거와 예상 증거 |
|---|---|
| CSV CI | ci.yml graph-core filter `graph-io/**`; job `Test / Core & TinkerGraph`의 `:bluetape4k-graph-io-csv:test`, Coverage Report, CI Status. PR SHA와 terminal conclusion/URL을 저장 |
| 8개 caller CI | examples.yml pull_request base develop, `examples/**` 및 `graph-io/**` filter. `Build / All Examples (Testcontainers)`의 100~108행에 표의 8개 module build 명시, `Examples Status` terminal result 및 test artifact 확인 |
| artifact | 기존 `io.bluetape4k:bluetape4k-graph-io-csv`, baseVersion=1.1.0 개발선. 버전/catalog/BOM·publish task 변경 없음. 게시 실행은 요청 밖이므로 N/A; 새 API는 기존 공개 함수 제거 없이 추가 |
| AC1~3 | test command/exit/JUnit XML/count, source diff, reviewer별 model/effort와 판정, 최종 commit SHA |
| AC4 | 8개 module별 test XML/count, 공개 선언 비교와 javap 출력, 최종 commit SHA |
| AC5~6 | README/KDoc/lesson paths, diffcheck, SPW readback, PR head·CI URL·reviews/threads |

실행 rollback 단위는 helper 구현과 8개 caller 이전을 포함하는 하나의 implementation commit이다. 필요 시 `git revert <implementation-sha>` 후 CSV 전체 test/detekt와 8개 example test를 같은 직렬 명령으로 다시 실행한다. spec/plan commit은 구현 코드를 바꾸지 않아 설명 이력으로 남긴다. PR 게시 후 문제가 발견되면 같은 head에 수정 commit을 push하고 기존 CI/review 증거를 무효화한다. artifact 게시 여부는 현재 작업에서 바꾸지 않으며 이미 게시된 버전의 문제는 별도 release gate에서 patch release로 복구한다.

## 5. PR과 종료

- [ ] 설계·계획은 구현 전에 Lore commit. 구현/test/docs/review를 별도 scoped commit으로 기록한다.
- [ ] repo bluetape4k/bluetape4k-graph, base develop, head refactor/issue-637-csv-resources로 개별 PR 생성. Fixes #637, assignee debop, milestone1.1.0 및 issue label 반영. guidance refresh와 live metadata readback.
- [ ] actual PR diff/CI/threads를 여섯 관점과 통합 재검토하고 exact-head terminal checks를 확인한다. 전체 #641 PR 준비 전까지 merge하지 않는다.

## 위험·복구

TCCL 변경: 전환 전 캡처와 finally 복원 테스트. 취소 cleanup: outer IO use 안에서 예외가 발생하도록 구조 고정. source escape: block 수명 명시와 금지 예시. 원인/close 예외 경쟁: identity와 suppressed 순서 검증. 공통화 후 실패는 helper+8개 호출자 commit을 함께 revert한다. 이미 release된 API 문제는 후속 patch release로 수정하며 tag/게시 artifact를 덮어쓰지 않는다. 신규 module·Spring·Exposed·JNI·JDK preview API 변경은 없다.

Writer SPW-01~05: 한국어 실행 계획, AC별 task/command·소유권·문서·hazard·rollback 매핑과 Markdown readback 완료. 아직 실행 결과를 주장하지 않는다.

Loader 탐색은 null 반환만 miss로 취급한다. SecurityException/RuntimeException은 같은 예외를 전파하고 fallback으로 우회하지 않는다. SecurityException을 던지는 TCCL fixture에서 예외 identity와 fallback 호출 0회를 검증한다.

## 구현 검증에서 확인한 취소 예외 보존

첫 구현은 CSV 82개 중 81개를 통과했지만 실제 Job 취소의 terminal cause에 close 실패가 남지 않았다. 중첩 withContext가 전달한 취소 예외와 호출자 Job의 원래 원인이 달랐다. callback 경계에서 CancellationException만 잡고 callerContext.ensureActive()로 호출자의 취소 원인을 전파한 다음 nested use가 닫기 실패를 suppressed로 붙이도록 보완했다. 호출자 Job이 취소되지 않은 callback 자체의 CancellationException은 그대로 다시 던진다. 자원 열기/닫기 dispatcher와 공개 계약은 유지한다. 재검증 637-helper-green2에서 82개 테스트와 detekt가 통과했다.

활성 Job에서 callback 자체가 CancellationException을 던지는 추가 회귀 테스트는 dispatcher 복귀 중 예외 identity 손실도 재현했다. callback과 IO 자원 처리 결과를 Result로 경계 너머에 전달하고, 자원을 소유한 use 내부 및 최종 호출자 위치에서 getOrThrow하여 같은 예외와 suppressed를 보존한다. 실제 Job 취소는 ensureActive로 먼저 전파한다. 637-helper-green4에서 동작 테스트 83개가 통과했으며, Throwable 전달 경계의 제한된 detekt suppression에는 원래 예외를 다시 던지는 목적을 명시했다.

## Pre-PR 안정성 P1의 재현과 수정

독립 architect(gpt-5.6-sol/high)는 callback 정상 반환 뒤 close/dispatcher 복귀 시점의 취소가 이미 발생한 close 실패를 버릴 수 있다고 지적했다. edge close 진입을 latch로 고정하고 Job 취소 후 edge/vertex close 실패를 발생시키는 테스트에서 suppressed 누락을 재현했다(637-close-race-red). IO 종료 전에 failure를 외부 지역 변수에 기록하고 바깥 withContext의 취소 경계에서 별도 failure를 원래 취소 예외에 보존한다. nested use의 edge→vertex 예외 연결은 그대로 유지한다. captureFailure의 실패 기록은 ensureActive보다 먼저 수행하므로 close가 CancellationException을 던지는 경우에도 기록을 잃지 않는다. 637-close-race-green의 동작 테스트 84개는 통과했고, 두 줄의 스타일 경고를 정리한 뒤 637-final-green에서 전체 검증을 진행한다.

진행 체크표 기준: pre-PR 구현·검증·리뷰까지 완료했다. 이후 commit/PR/hosted CI/post-PR 진행의 최신 상태는 PR 본문의 DoD와 실행 checklist에 기록하며, 이 계획의 pre-PR snapshot을 이미 수행한 hosted 검증 증거로 해석하지 않는다.

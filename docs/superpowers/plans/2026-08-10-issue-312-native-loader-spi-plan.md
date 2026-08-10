# Issue #312 Backend-Native Bulk Loader SPI Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

Goal: graph-io-core에 backend-native bulk loader SPI를 추가해 후속 backend adapter가 source validation, capabilities, lifecycle, progress, bounded failure report를 공유한다.

Architecture: 기존 GraphBulkImporter는 수정하지 않고 io.bluetape4k.graph.io.nativebulk를 additive API로 추가한다. Request의 raw R과 validator가 반환하는 validated V를 다른 generic으로 분리하고, base loader가 lifecycle gate, deadline/cancellation token, source cleanup, redacted exception boundary, progress verifier, report postcondition을 최종 orchestration으로 강제한다. 이 issue에는 실제 backend adapter, URI/file I/O, staging, Testcontainers, 새 dependency가 없다.

Tech Stack: Kotlin 2.4, JDK/JVM 25, Gradle 9.7.0, java.time.Duration,
ReentrantLock/Condition, AtomicBoolean/AtomicLong/AtomicReference, Gradle
:bluetape4k-graph-io-core, JUnit 5, Bluetape assertions.

---

## 파일 구조와 범위

- Create graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoadModels.kt
  - enum, shared `internal` immutable/log-safe/deadline helpers, exact URI origin/policy, request, capabilities, progress, failure, redacted exception, report.
- Create graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoadSource.kt
  - one-shot serialized validated source, execution context, cancellation token, source validator.
- Create graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoader.kt
  - progress verifier, generic R/V loader state machine, cancellation/close hooks, unsupported loader.
- Create graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoadModelsTest.kt
- Create graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoaderTest.kt
- Modify graph-io/core/README.md and graph-io/core/README.ko.md
- Create docs/superpowers/reviews/2026-08-10-issue-312-plan-review.md
- Create docs/lessons/2026-08-10-issue-312-native-loader-spi.md

제외 파일은 모든 graph backend adapter, GraphBulkImporter 계약, Gradle catalog/dependency, workflow, Testcontainers 설정이다.

## Traceability와 risk

| 설계 기준 | 구현 task | 증거 |
|---|---|---|
| raw R/validated V 분리 | Task 2, 4 | compiler signature와 validator negative test |
| URI default deny와 exact scheme-host-port origin | Task 2 | policy bound/mismatch tests |
| monotonic deadline와 durable cancellation | Task 2, 4 | validation-close race와 interrupt test |
| redacted exception 및 listener identity | Task 4 | raw adapter/cleanup failure test |
| report/capability invariant | Task 2, 4 | zero-record, atomic rollback, partial, detail bound |
| phase/count/callback verifier | Task 4 | event-kind boundary, regression, terminal count/outcome coupling, 105 callback |
| source take/close serialization | Task 2 | take-close race test |
| bounded shutdown and deferred cleanup | Task 4 | single close timeout, load-finish cleanup owner, capability mismatch |
| lifecycle diagnostics | Task 4 | secret-free STARTED/terminal/CLOSED event and observer-failure isolation |
| validator provisional rollback | Task 2, 4 | validation context rollback on partial acquisition/cancellation |
| portable lane과 native lane 분리 | Task 5 | 양쪽 README와 diff scope |

고위험 신호와 대응은 다음과 같다.

1. public API가 기존 파일을 수정하거나 새 dependency를 요구하면 additive package로 범위를 되돌린다.
2. close/load race가 command를 시작시키면 token을 validation과 execution 양쪽에 전달하고 validation 직후 check를 유지한다.
3. raw Throwable, path, URI, command가 밖으로 나오면 base mapping을 GraphNativeBulkLoadException fixed code로 고정하고 listener exception만 primary로 보존한다.
4. callback 수나 failure list가 증가하면 stateful verifier와 bounded snapshot을 수정하며 backend benchmark를 추가하지 않는다.
5. BACKEND_SERVER가 exact origin 재검증을 선언하지 않으면 capability 생성 자체를 실패시킨다.
   URI를 지원하지 않는 FILE/DIRECTORY 서버 staging도 artifact binding 재검증을
   선언할 수 있도록 URI origin allowlist와 backend origin/artifact revalidation flag를 분리한다.
6. 임의 native cancellation/cleanup이 bounded하지 않으면 `shutdownGuarantee = UNKNOWN`
   및 `supported = false`로 고정한다. close grace 만료 뒤에는 load 종료 thread가
   deferred cleanup owner를 원자적으로 획득해 두 번째 `close()` 없는 종료를 보장한다.
7. raw cause/suppressed를 public boundary로 전파하지 않으면서도 운영 진단을 잃지
   않도록 secret-free diagnostic observer를 STARTED/terminal/CLOSED에 발행한다.
8. close/cancellation/validated cleanup/diagnostic observer는 deadline-aware
   virtual-thread bounded call로 실행한다. deadline을 무시하는 fake는 caller에
   TIMEOUT을 반환하더라도 실제 worker completion 전에는 `CLOSED`를 publish하지
   않는다. interrupt를 관찰하는 cooperative fake가 종료하면 completion callback이
   deferred owner로 정확히 한 번 terminal cleanup과 `CLOSED`를 publish한다. observer
   timeout은 단일 in-flight 작업과 circuit breaker로 추가 zombie worker를 만들지 않는다.

## 계획 review gate

- [x] Step 1: performance, stability, security, operator/Ops, developer/API, user/caller 여섯 read-only lane과 main integration을 실행한다.
- [x] Step 2: 각 finding을 P0/P1/P2/P3로 정규화하고 P0=0, P1=0이 될 때까지 영향을 받은 lane을 재실행한다. ReentrantLock/Condition, non-null cancellation trigger, Serializable model, CONTRACT_VIOLATION, deferred cleanup, shutdown guarantee, diagnostic observer를 implementation readiness 기준으로 확인한다.
- [x] Step 3: docs/superpowers/reviews/2026-08-10-issue-312-plan-review.md에 관점별 수치, 근거, 조치, 최종 verdict를 기록한다.

P0/P1이 남아 있으면 구현을 시작하지 않는다. P2/P3는 계획 수정, 후속 issue, 또는 근거 있는 N/A로 명시한다.

## Task 1: 모델 RED 테스트

Files:
- Create graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoadModelsTest.kt

- [ ] Step 1: request source 비노출, fixed `native-bulk-load` operation label, Serializable marker/serialVersionUID, exact origin snapshot, URI entry/byte/port/hop/length bound, unsupported capability mismatch 테스트를 작성한다.
- [ ] Step 2: CountingValidatedSource fake로 takeOnce와 closeOnce를 제공하고 첫 take 성공, 두 번째 take 실패, close 이후 take 실패, take 중 close가 closeOnce를 기다리는 race를 작성한다.
- [ ] Step 2a: closeOnce가 모든 독립 자원을 terminal invocation에서 시도·집계하고, ReentrantLock/Condition wait 중 interrupt flag를 기록·복원한 뒤 여러 close에도 한 번만 실행되는지 작성한다. in-flight `takeOnce()`로 `close()`가 grace timeout을 반환해도 take 종료 thread가 deferred close owner가 되어 두 번째 `close()` 없이 `closeOnce()`를 실행하고 `CLOSED`를 publish하는지 검증한다.
- [ ] Step 2b: validator가 `GraphNativeBulkLoadValidationContext`에 provisional staging/session close를 등록한 뒤 실패·취소하는 경우 역순 rollback과 redacted suppressed aggregation을 검증한다. rollback은 하나의 deadline-bound owner call로 실행하고, timeout 뒤 pending completion을 추적하며 추가 worker를 만들지 않는지 검증한다.
- [ ] Step 3: request+capabilities를 함께 받는 report factory의 zero-record FAILED, COMPLETED count equality, PARTIAL durable success+failure, ATOMIC non-completed zero durable count, NONE detail list, operation/detail-limit mismatch를 작성한다.
- [ ] Step 4: 구현 전 RED를 확인한다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoadModelsTest' --no-daemon --no-configuration-cache

Expected: 새 nativebulk type이 없어서 compile failure.

테스트는 io.bluetape4k.assertions.assertFailsWith와 shouldBeEqualTo 계열만 사용하고 assertThrows를 추가하지 않는다.

## Task 2: immutable model과 source boundary

Files:
- Create GraphNativeBulkLoadModels.kt
- Create GraphNativeBulkLoadSource.kt

- [ ] Step 1: SourceKind, TransactionGuarantee, FailureDetail, Phase, Outcome, CancellationReason, UriAccess, SourceExecution, fixed FailureCode를 선언한다.
- [ ] Step 2: ASCII log-safe helper와 immutable set/list snapshot을 구현한다. Request R의 toString에는 source와 operationName을 포함하지 않는다.
- [ ] Step 3: UriOrigin(scheme, canonicalHost, port)를 검증하고 SourcePolicy exact-origin set, max 32 entries, max 4096 origin bytes, max URI length, max 5 redirect hops, credential/private-network/redirect/staging/backend revalidation flags를 검증한다.
- [ ] Step 4: Capabilities가 supported/sourceKinds/URI policy/backend-server revalidation/approved staging invariants를 constructor에서 검증하도록 한다.
- [ ] Step 5: Progress의 local count invariant, Failure의 fixed message, Report의 bounded snapshot/count/outcome/cancellation/elapsed invariant와 requireCompatible를 구현한다. PARTIAL은 durable success와 failed record 및 retained/omitted detail이 모두 있어야 하고, ATOMIC non-completed report는 durable count 0이어야 한다.
- [ ] Step 6: CancellationToken이 monotonic start/timeout, overflow-safe remainingNanos, 최대 365일 finite timeout, non-null timeout/interrupt/close/listener-failure trigger와 hook exactly-once를 제공하도록 한다. `check()`가 timeout/interrupt를 발견하면 같은 bounded hook을 원자적으로 호출한다. `GraphNativeBulkLoadDeadline`과 virtual-thread bounded call이 timeout을 감시한다. Execution은 effective timeout/deadline을 노출하고, GraphNativeBulkLoadException은 adapter-origin raw cause/suppressed를 새 redacted boundary로 복사한다.
- [ ] Step 7: `GraphNativeBulkLoadValidationContext` provisional rollback과 ValidatedSource V의 takeOnce/closeOnce를 ReentrantLock/Condition/state로 직렬화하고 take 이후 close race, close 이후 take, repeated close를 결정적으로 처리한다. Execution V는 source를 보유하지만 toString에는 source/operationName을 포함하지 않는다.
- [ ] Step 8: 모델 테스트 GREEN을 확인한다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoadModelsTest' --no-daemon --no-configuration-cache

Expected: 모델 및 source boundary 테스트 PASS.

## Task 3: loader RED 테스트

Files:
- Create graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoaderTest.kt

- [ ] Step 1: R=RawSource, V=ValidatedArtifact인 fake validator/loader를 정의한다. loadValidated에는 raw request/source 파라미터가 없고 execution.source.take만 사용할 수 있게 한다.
- [ ] Step 2: closed/concurrent load가 validator에 도달하지 않는지, validator가 throw할 때 command가 0회이고 state가 복귀하는지 테스트한다.
- [ ] Step 3: close가 validation 중 token을 request하고 validation 이후 base check가 command 시작을 차단하는 race를 CountDownLatch로 테스트한다.
- [ ] Step 4: listener가 throw할 때 cancellation hook failure와 source close failure가 있어도 동일 listener Throwable이 primary인지 테스트한다.
- [ ] Step 5: raw validator/command/cleanup/cancellation/closeResources exception이 fixed redacted code로 매핑되고, invalid report/progress postcondition은 `CONTRACT_VIOLATION`으로 구분되는지 테스트한다.
- [ ] Step 6: progress regression, phase regression, callback thread mismatch, duplicate COMPLETE, missing COMPLETE, terminal outcome mismatch, listener null에서도 verifier가 동작하는지 테스트한다.
- [ ] Step 7: 100,000 processed와 interval 1,000에서 callback <= 105, interval 1 절대 상한, phase-only unknown count, caller thread identity를 테스트한다.
- [ ] Step 8: deadline 초과 후 native 성공을 차단하고, bounded close grace가 CLOSING을 유지한 채 redacted timeout을 반환한 뒤 두 번째 close 없이 load 종료 thread가 cleanup하는지, validated source의 in-flight `takeOnce()` timeout 뒤에도 take 종료 thread가 deferred `closeOnce()`를 수행하는지, interrupted close가 load 종료와 resource close를 기다리고 interrupt status를 복원하는지, close/cancel hook failure가 서로 suppressed로 누적되는지, UnsupportedGraphNativeBulkLoader가 fixed exception으로 실패하는지 테스트한다. hanging cancellation/closeOnce/closeResources fake는 interrupt 후 completion 전 `CLOSED`가 금지되고 cooperative 종료 시에만 deferred close가 publish되는지 검증한다. `takeOnce()`/`loadValidated()`가 deadline 내 cancellation을 관찰하지 않는 fake는 `UNKNOWN + supported=false` capability mismatch로 거절되는지 검증한다. observer timeout은 parent close/load deadline을 넘겨 호출자를 지연시키지 않고 단일 in-flight/circuit breaker로 추가 worker를 만들지 않으며, expired close timeout도 `CANCELLED/TIMEOUT` diagnostic dispatch를 유실하지 않는지 검증한다. provisional validation rollback은 단일 owner call과 pending completion 추적을 사용하고 deadline 뒤 새 worker를 시작하지 않는지, fixed operation-label canary, 동일 diagnosticId correlation과 report outcome mapping도 검증한다.
- [ ] Step 9: 구현 전 RED를 확인한다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoaderTest' --no-daemon --no-configuration-cache

Expected: loader type 부재로 compile failure.

## Task 4: final loader orchestration

Files:
- Create GraphNativeBulkLoader.kt

- [ ] Step 1: GraphNativeBulkLoadProgressVerifier를 listener 유무와 무관하게 설치한다. PHASE/INTERVAL event kind, phase transition/token boundary, cumulative counts, caller thread, one COMPLETE, callback budget, terminal count/outcome/report equality를 검사한다.
- [ ] Step 2: GraphNativeBulkLoader<R,V>의 OPEN/LOADING/CLOSING/CLOSED gate를 구현하고 validator 호출 전에 LOADING을 선형화한다.
- [ ] Step 3: capability/source-kind 선행 gate 후 load 시작 시 token을 만들고 4-인자 `validator(request, capabilities, token, validationContext)`를 호출한 뒤 token.check를 다시 수행한다. execution에는 V source와 같은 token만 전달한다.
- [ ] Step 4: validator raw failure, command raw failure, invalid report/progress, cleanup failure를 fixed GraphNativeBulkLoadException으로 매핑한다.
- [ ] Step 5: listener failure wrapper는 cancellation hook을 안전하게 호출하고 hook 실패를 redacted suppressed로 보존한 뒤 원본 Throwable을 primary로 유지하며, source close redacted failure도 suppressed로만 추가한다.
- [ ] Step 6: close는 durable CLOSE/INTERRUPT token request, bounded close grace, hook failure capture, ReentrantLock/Condition wait, interrupt flag restore, deadline-aware virtual-thread bounded terminal resource attempt, single closeResources owner와 CLOSED publish를 보장한다. grace timeout 뒤 load 종료 시 deferred cleanup owner를 자동 획득하며, `shutdownGuarantee = BOUNDED` capability만 supported로 허용한다.
- [ ] Step 6a: `GraphNativeBulkLoadDiagnosticObserver`에 secret-free bounded STARTED/COMPLETED/FAILED/CANCELLED/CLOSED event를 발행하고 observer 예외가 load/close 결과를 바꾸지 않도록 한다. 고정 `native-bulk-load` label과 load/close 단위 correlation `diagnosticId`를 사용하고, report outcome에서 terminal kind를 도출한다. KLogging adapter가 diagnostic fields만 구조화해 기록할 수 있는 contract를 고정한다.
- [ ] Step 7: UnsupportedGraphNativeBulkLoader<R,V>는 default-deny unsupported capabilities와 fixed UNSUPPORTED_SOURCE exception만 노출한다.
- [ ] Step 8: loader GREEN을 확인한다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoaderTest' --no-daemon --no-configuration-cache

Expected: lifecycle, security boundary, progress, cancellation, unsupported tests PASS.

## Task 5: README와 호출 경계

Files:
- Modify graph-io/core/README.md
- Modify graph-io/core/README.ko.md

- [ ] Step 1: GraphBulkImporter portable record loop와 GraphNativeBulkLoader backend-owned command lane을 비교한다.
- [ ] Step 2: R raw source, V validated artifact, cancellation token, capabilities, progress/report, unsupported behavior를 API example로 설명한다.
- [ ] Step 3: URI default deny, exact origin/redirect hop, BACKEND_SERVER 재검증, caller-owned source close 보존, 실제 backend adapter/I/O/Testcontainers 비포함을 명시한다.
- [ ] Step 4: Korean README는 reader-facing prose를 한국어로 작성하고 code/API/URL은 보존한다.
- [ ] Step 5: git diff --check를 실행한다.

## Task 6: 통합 검증

- [ ] Step 1: targeted nativebulk tests와 compileKotlin을 순차 실행한다.
- [ ] Step 2: graph-io-core 전체 test를 실행하고 baseline 82개 이상 통과를 확인한다.
- [ ] Step 3: performance/stability scan으로 100,000 failure bounded detail, callback <= min(1,024, 5 + ceil(processed / progressInterval)), interval 1 cap, take/close race, bounded close grace, interrupted close를 재확인한다.
- [ ] Step 3a: diagnostic event가 backend/operation label/phase/elapsed/outcome/code/diagnosticId만 포함하고 raw source·cause·suppressed·URI가 없는지, close grace 후 deferred cleanup과 bounded capability mismatch를 재확인한다.
- [ ] Step 4: 실제 backend/Testcontainers/URI dereference는 이 issue 범위가 아니므로 N/A를 기록한다.
- [ ] Step 5: git diff --name-only origin/develop...HEAD, git diff --check, plan/spec acceptance traceability를 확인한다.

Commands:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.*' --no-daemon --no-configuration-cache
    ./gradlew :bluetape4k-graph-io-core:compileKotlin --no-daemon --no-configuration-cache
    ./gradlew :bluetape4k-graph-io-core:test --no-daemon --no-configuration-cache
    git diff --check

## Task 7: lesson과 Lore commit

Files:
- Create docs/lessons/2026-08-10-issue-312-native-loader-spi.md

- [ ] Step 1: 범위, R/V boundary, token, redaction, progress verifier, exact origin 선택과 실제 validation evidence를 Korean lesson으로 기록한다.
- [ ] Step 2: 실제 adapter/URI dereference/Testcontainers를 후속 issue로 둔 이유와 다음 adapter가 재검증할 staging/DNS/cancellation을 기록한다.
- [ ] Step 3: 변경 파일을 하나씩 검토하고 기존 worktree dirty/untracked path가 섞이지 않았는지 확인한다.
- [ ] Step 4: 아래 Lore trailers를 포함한 Korean commit을 만든다.

    native loader SPI의 검증·수명 경계를 고정한다

    Constraint: 실제 backend adapter와 URI/file I/O는 후속 이슈 범위다
    Rejected: 기존 GraphBulkImporter 확장 | native command와 portable record loop가 다르다
    Confidence: high
    Scope-risk: moderate
    Directive: 후속 adapter는 validator artifact와 cancellation token을 backend 경계에서 재검증한다
    Tested: nativebulk tests, graph-io-core test, compileKotlin, git diff --check
    Not-tested: 실제 backend/Testcontainers/URI DNS rebinding

## Stop condition

nativebulk tests, 전체 graph-io-core test/compile, diff check가 fresh PASS이고 final review P0=0/P1=0이면 로컬 feature branch에서 종료한다. PR, merge, release, milestone close는 별도 사용자 요청 없이는 수행하지 않는다.

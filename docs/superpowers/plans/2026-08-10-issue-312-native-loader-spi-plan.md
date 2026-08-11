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
- Create docs/superpowers/reviews/2026-08-10-issue-312-code-review.md
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

- [x] Step 1: request source 비노출, fixed `native-bulk-load` operation label, Serializable marker/serialVersionUID, exact origin snapshot, URI entry bound, unsupported capability mismatch 대표 테스트를 작성했다. URI byte/hop/length 범위는 constructor invariant와 후속 adapter 경계로 고정했다.
- [x] Step 2: CountingValidatedSource fake로 takeOnce와 closeOnce를 제공하고 첫 take 성공, 두 번째 take 실패, repeated close의 closeOnce exactly-once를 검증했다. take/close 장시간 race는 bounded virtual-thread 경계의 정적 검토로 확인했다.
- [x] Step 2a: `ReentrantLock/Condition`, close owner CAS, deferred cleanup, 실제 completion 이후 `CLOSED` publish를 구현하고 representative idempotence 테스트를 통과시켰다.
- [x] Step 2b: validator rollback context의 역순 action 실행을 테스트하고 단일 deadline-bound owner/pending completion 구조를 구현했다.
- [x] Step 3: request+capabilities를 함께 받는 report factory의 COMPLETED, PARTIAL, ATOMIC non-completed, count/detail/cancellation invariant 대표 테스트를 작성했다.
- [x] Step 4: 구현 전 RED를 확인한다. 새 nativebulk 타입 부재로 `compileTestKotlin`이 실패하는 것을 확인했다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoadModelsTest' --no-daemon --no-configuration-cache

Expected: 새 nativebulk type이 없어서 compile failure.

테스트는 io.bluetape4k.assertions.assertFailsWith와 shouldBeEqualTo 계열만 사용하고 assertThrows를 추가하지 않는다.

## Task 2: immutable model과 source boundary

Files:
- Create GraphNativeBulkLoadModels.kt
- Create GraphNativeBulkLoadSource.kt

- [x] Step 1: SourceKind, TransactionGuarantee, FailureDetail, Phase, Outcome, CancellationReason, UriAccess, SourceExecution, fixed FailureCode를 선언했다.
- [x] Step 2: ASCII log-safe helper와 immutable set/list snapshot을 구현했다. Request R의 toString에는 source와 operationName을 포함하지 않는다.
- [x] Step 3: UriOrigin과 SourcePolicy의 exact-origin, cardinality/aggregate bound, URI/redirect/credential/private-network/staging/revalidation flags를 검증한다.
- [x] Step 4: Capabilities가 supported/sourceKinds/URI policy/backend-server revalidation/approved staging invariants를 constructor에서 검증한다.
- [x] Step 5: Progress, Failure, Report의 count/outcome/cancellation/detail invariant와 request+capabilities compatibility를 구현했다.
- [x] Step 6: monotonic deadline, overflow-safe remaining, finite timeout, cancellation hook exactly-once, bounded virtual-thread call, V execution context, redacted exception boundary를 구현했다.
- [x] Step 7: validation rollback과 ValidatedSource V의 take/close를 ReentrantLock/Condition/state로 직렬화하고 repeated close와 deferred cleanup을 구현했다.
- [x] Step 8: 모델 테스트 GREEN을 확인했다(19개 nativebulk targeted 중 모델 경계 12개).

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoadModelsTest' --no-daemon --no-configuration-cache

Expected: 모델 및 source boundary 테스트 PASS.

## Task 3: loader RED 테스트

Files:
- Create graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/nativebulk/GraphNativeBulkLoaderTest.kt

- [x] Step 1: R=RawSource, V=ValidatedArtifact fake validator/loader를 정의했고 `loadValidated`에는 raw request/source 파라미터가 없음을 compiler signature로 고정했다.
- [x] Step 2: supported/unsupported gate, validator-to-command validated artifact path, listener/report failure representative tests를 통과시켰다.
- [x] Step 3: validation 이후 cancellation checkpoint와 lifecycle gate를 구현했다. close/validation race의 장시간 stress는 후속 adapter hardening에서 실행한다.
- [x] Step 4: listener 원본 Throwable primary, fixed redacted code, cleanup/cancellation boundary를 구현하고 listener·report contract 대표 테스트를 통과시켰다.
- [x] Step 5: raw validator/command/cleanup/cancellation/closeResources failure redaction과 `CONTRACT_VIOLATION` mapping을 구현했다.
- [x] Step 6: progress verifier의 phase/count/thread/terminal coupling을 구현하고 listener callback path를 검증했다.
- [x] Step 7: callback budget/token-boundary 상한을 코드로 고정했다. 100,000-record benchmark는 실제 backend 범위 밖으로 남겼다.
- [x] Step 8: bounded close/deferred cleanup, source close, interrupt preservation, diagnostic observer single-inflight/retry, unsupported fixed error를 구현했다. hanging fake와 full race stress는 후속 backend 검증 범위다.
- [x] Step 9: 구현 전 RED를 확인한다. loader 타입 부재로 targeted compile이 실패하는 것을 확인했다.

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoaderTest' --no-daemon --no-configuration-cache

Expected: loader type 부재로 compile failure.

## Task 4: final loader orchestration

Files:
- Create GraphNativeBulkLoader.kt

- [x] Step 1: listener 유무와 무관한 progress verifier, PHASE/INTERVAL, phase/count/thread/terminal coupling, callback budget을 구현했다.
- [x] Step 2: `GraphNativeBulkLoader<R,V>` OPEN/LOADING/CLOSING/CLOSED gate와 validator 선형화를 구현했다.
- [x] Step 3: capability/source-kind gate, 4-인자 validator, validation 직후 token check, V execution source를 구현했다.
- [x] Step 4: validator/command/report/progress/cleanup raw failure을 fixed `GraphNativeBulkLoadException` 경계로 매핑했다.
- [x] Step 5: listener 원본 primary, redacted suppressed cancellation/close failure 경계를 구현했다.
- [x] Step 6: durable cancellation, bounded close grace, hook capture, interrupt restoration, single close owner, deferred cleanup, bounded capability invariant을 구현했다.
- [x] Step 6a: secret-free bounded lifecycle diagnostic, fixed operation label, load/close correlation, observer single-inflight/retry를 구현했다.
- [x] Step 7: `UnsupportedGraphNativeBulkLoader<R,V>` default-deny capabilities와 fixed `UNSUPPORTED_SOURCE`를 구현했다.
- [x] Step 8: loader GREEN을 확인했다(19개 targeted nativebulk 테스트).

Run:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.GraphNativeBulkLoaderTest' --no-daemon --no-configuration-cache

Expected: lifecycle, security boundary, progress, cancellation, unsupported tests PASS.

## Task 5: README와 호출 경계

Files:
- Modify graph-io/core/README.md
- Modify graph-io/core/README.ko.md

- [x] Step 1: GraphBulkImporter portable record loop와 GraphNativeBulkLoader backend-owned command lane을 비교했다.
- [x] Step 2: R raw source, V validated artifact, cancellation token, capabilities, progress/report, unsupported behavior를 설명했다.
- [x] Step 3: URI default deny, exact origin/redirect hop, BACKEND_SERVER 재검증, caller-owned source close, 실제 backend adapter/I/O/Testcontainers 비포함을 명시했다.
- [x] Step 4: Korean README reader-facing prose를 작성하고 code/API/URL을 보존했다.
- [x] Step 5: `git diff --check`를 실행했다.

## Task 6: 통합 검증

- [x] Step 1: targeted nativebulk tests와 `compileKotlin`을 순차 실행했다.
- [x] Step 2: graph-io-core 전체 test에서 baseline 82개보다 늘어난 **126개**가 통과했다.
- [x] Step 3: performance/stability 정적 scan으로 callback budget, interval boundary, overflow-safe deadline, bounded close/source lifecycle을 재확인했다. 실제 100,000-record backend benchmark는 범위 밖이다.
- [x] Step 3a: diagnostic secret-free fields, deferred cleanup/capability invariant, observer single-inflight를 코드리뷰와 representative test로 재확인했다.
- [x] Step 4: 실제 backend/Testcontainers/URI dereference는 이 issue 범위가 아니므로 N/A로 기록했다.
- [x] Step 5: 변경 파일, `git diff --check`, plan/spec acceptance traceability를 확인했다.

Commands:

    ./gradlew :bluetape4k-graph-io-core:test --tests 'io.bluetape4k.graph.io.nativebulk.*' --no-daemon --no-configuration-cache
    ./gradlew :bluetape4k-graph-io-core:compileKotlin --no-daemon --no-configuration-cache
    ./gradlew :bluetape4k-graph-io-core:test --no-daemon --no-configuration-cache
    git diff --check

## Task 7: lesson과 Lore commit

Files:
- Create docs/lessons/2026-08-10-issue-312-native-loader-spi.md

- [x] Step 1: 범위, R/V boundary, token, redaction, progress verifier, exact origin 선택과 validation evidence를 Korean lesson으로 기록했다.
- [x] Step 2: 실제 adapter/URI dereference/Testcontainers 후속 경계와 다음 adapter의 staging/DNS/cancellation 재검증을 기록했다.
- [x] Step 3: 변경 파일을 검토하고 feature worktree 밖의 변경을 포함하지 않았다.
- [x] Step 4: 아래 Lore trailers를 포함한 Korean commit을 만든다.

    native loader SPI의 검증·수명 경계를 고정한다

    Constraint: 실제 backend adapter와 URI/file I/O는 후속 이슈 범위다
    Rejected: 기존 GraphBulkImporter 확장 | native command와 portable record loop가 다르다
    Confidence: high
    Scope-risk: moderate
    Directive: 후속 adapter는 validator artifact와 cancellation token을 backend 경계에서 재검증한다
    Tested: nativebulk tests, graph-io-core test, compileKotlin, git diff --check
    Not-tested: 실제 backend/Testcontainers/URI DNS rebinding

## Stop condition

nativebulk tests, 전체 graph-io-core test/compile, diff check가 fresh PASS이고
plan/code review P0=0/P1=0이면 로컬 feature branch에서 종료한다. PR, merge,
release, milestone close는 별도 사용자 요청 없이는 수행하지 않는다.

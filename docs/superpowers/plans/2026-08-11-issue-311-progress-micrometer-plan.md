# #311 graph-io 진행 리스너와 Micrometer bridge 구현 계획

## 기준과 완료 조건

- 기준 브랜치: `origin/develop` (`5ec93ef4b98e1654480bf831b13defd5aae057b7`)
- worktree: `.worktrees/issue-311-progress-micrometer`
- toolchain: Kotlin 2.4, JVM/JDK 25, `-jvm-default=enable`, Gradle 9.7 계열
- 기존 3-인자 import/export API와 기존 report 결과를 변경하지 않는다.
- core는 Micrometer를 참조하지 않는다.
- 이 이슈의 `bytes/file` 수용 기준은 단일 public entrypoint의 논리 bytes와
  source/sink file 메타데이터가 확인되는 경우를 의미한다. 여러 파일을 한 번에
  처리하는 aggregate file-count meter는 #311 범위에서 제외하고, 파일 경로·이름은
  event/tag/log에 노출하지 않는다.
- 동기/suspend/Virtual Thread 및 CSV/Jackson2/Jackson3/GraphML/Okio의
  public entrypoint마다 reporter lifecycle은 정확히 한 번이다.
- 완료 판정은 기능 테스트, classpath/back-off 테스트, module registration,
  README locale parity, `git diff --check`, Type A final review까지 포함한다.

## 단계별 실행

### 1. Core 계약과 red gate

먼저 다음 실패 테스트를 추가하고 실행하여 새 API가 아직 없음을 확인한다.

- `GraphIoProgressEventTest`: serialization, non-negative/count/skip/bytes/
  duration invariant, `hasStarted` pre-start cancellation rule
- `GraphIoProgressReporterTest`: `NEW → STARTED → TERMINAL`, same-run ordering,
  concurrent run isolation, re-entrant callback, duplicate terminal CAS,
  listener Exception redaction/continuation, listener Error primary/suppressed,
  Java null listener rejection
- `GraphIoCompositeProgressListenerTest`: ordered delegates, Exception isolation,
  Error collection/rethrow after later delegates, empty-list NOOP, and a reporter
  warning hook that records one fixed redacted warning per delegate `Exception`
  without retaining the cause, message, path, or record identifier
- contract compatibility smoke: existing 2/3-argument Kotlin calls, Java caller,
  and a fixture compiled into a separate artifact from the baseline
  `5ec93ef4b98e1654480bf831b13defd5aae057b7` before the new core API is built;
  link that precompiled 3-argument implementation against the new core with
  `-jvm-default=enable` (same-build fixtures are not accepted)
- cancellation tests: suspend cancellation, pre-start future cancel,
  `CompletableFuture.cancel(false)` and `cancel(true)` before start, start/cancel
  race, mid-run interrupt, worker actually observing interruption, late completion
  without second terminal, and exactly-once source/sink close

그 다음 production core를 구현한다.

- `GraphIoOperation`, `GraphIoProgressEventType`, `GraphIoProgressEvent`,
  `GraphIoProgressListener`, `GraphIoCompositeProgressListener`
- internal `GraphIoProgressReporter` with atomic run id/state, monotonic snapshot,
  fixed warning redaction and `try/finally` cleanup ordering
- required-listener overloads in sync, suspend and Virtual Thread contracts;
  old methods remain the compatibility path. Interface defaults validate the
  listener and delegate to legacy implementations without synthesizing events,
  because an external legacy implementation does not expose operation/format
  metadata; built-in format implementations override the overloads and emit the
  full lifecycle.
- cancellable Virtual Thread wrapper that owns the worker future and interrupt
  bridge; `cancel(false)` is a deterministic state-only cancellation that does
  not interrupt a started worker, while `cancel(true)` requests interruption;
  both branches are tested and neither relies on raw `CompletableFuture.cancel`
- KDoc and public serialization UID/locale-safe tag helpers

Red gate: `./gradlew :bluetape4k-graph-io-core:test` must pass before format edits.

### 2. Format and Okio wiring

Each implementation has one lifecycle owner and passes the same internal reporter
to delegates. Delegates never create a second reporter.

- CSV sync/suspend/Virtual Thread: vertex/edge phase progress, counts, skipped and
  failure totals, path byte totals where available. If the compatibility report
  has no phase stopwatch, `PHASE_COMPLETED.phaseElapsed` uses the report aggregate
  elapsed as an explicitly documented fallback; precise phase timing remains a
  follow-up format-specific hook.
- Jackson2 and Jackson3 sync/suspend/Virtual Thread: envelope read/create/write
  phase progress, malformed/overflow failure snapshots
- GraphML sync/suspend/Virtual Thread: reader/writer phase progress and typed
  failure snapshots
- Okio sync facade, DAEAD/gzip wrappers, suspend Flow/Await and Virtual Thread:
  format dispatch forwards reporter once; logical bytes are reported only when
  the wrapper can prove them, otherwise null
- format-specific options overloads receive required listener last; old overloads
  delegate to the no-listener compatibility path

Tests are added beside each format and must assert event ordering, terminal
exactly-once, report/count equality, phase timer input (including the aggregate
elapsed fallback for report-only compatibility paths), listener failure isolation,
and no duplicate event from nested Okio delegation. Virtual-thread tests use
latches/barriers to cover cancel-before-start, start/cancel race, mid-run
interrupt, late completion, actual worker stop, and exactly-once close.
Terminal cleanup tests also cover close failure/suppressed aggregation,
cancel-versus-close races, and listener `Error` after cleanup has completed.

Red/green gate:

```text
./gradlew :bluetape4k-graph-io-csv:test
./gradlew :bluetape4k-graph-io-jackson2:test
./gradlew :bluetape4k-graph-io-jackson3:test
./gradlew :bluetape4k-graph-io-graphml:test
./gradlew :bluetape4k-graph-okio:test
```

### 3. Optional Micrometer module

Add `graph-io/micrometer` with `api(project(":bluetape4k-graph-io-core"))`,
`api("io.micrometer:micrometer-core")`, and the centrally managed Micrometer
BOM. Implement `GraphIoMicrometerProgressListener` with only fixed
`Locale.ROOT` enum tags and registry-scoped eight-cell active gauges.

Tests use `SimpleMeterRegistry` and cover:

- exact terminal mapping (`successful*`, skipped, failures, processed bytes)
- operation/format/status and phase timer values
- duplicate/invalid/runId-zero event handling
- concurrent runs, cancellation before/after start, active gauge never negative
- no path, label, record id, exception message/class, or run id in meter tags

Update settings auto-inclusion, graph BOM/publication metadata, Kover aggregation,
CI smoke/Nightly graph-io scope, and both module READMEs.

### 4. Spring Boot opt-in wiring

Add compile-only bridge/Micrometer dependencies to `graph-spring-boot` and a
nested-condition `GraphIoMicrometerAutoConfiguration`:

- outer `@ConditionalOnClass(name=...)` and property
  `bluetape4k.graph.io.metrics.enabled=true` (`matchIfMissing=false`)
- nested configuration owns all `MeterRegistry`/bridge type references and
  `@ConditionalOnBean(MeterRegistry::class)`
- concrete bridge bean is named `graphIoMicrometerProgressListener` and has
  `autowireCandidate=false`; no generic alias is created. README/KDoc and tests
  use `@Resource(name=...)` or explicit context lookup, then build an explicit
  composite for user callback plus metrics
- MeterRegistry is supplied by Boot/Actuator; auto-config does not create or wrap
  importers/exporters

`ApplicationContextRunner` tests cover property false/missing, positive registry,
missing registry, `FilteredClassLoader` without Micrometer/bridge, user listener
plus explicit bridge/composite injection, and unqualified generic injection not
becoming ambiguous. The concrete bean is named
`graphIoMicrometerProgressListener` and `autowireCandidate=false`; tests use
`@Resource(name=...)`/explicit context lookup only and assert no generic alias is
created. Register the auto-config in `AutoConfiguration.imports` and include the
test-runtime bridge/Micrometer dependencies required by the positive context
tests.

### 5. Documentation and lesson

Update graph-io and graph-spring-boot `README.md`/`README.ko.md` together with:

- listener overload examples for sync/suspend/Virtual Thread
- Micrometer dependency and opt-in property
- concrete bridge injection and composite listener example
- bounded tags, logical bytes, callback non-blocking and cancellation semantics

Update `WIP.md`, `CHANGELOG.md`, and a Korean lesson under `docs/lessons/` with
the issue link, design decisions, validation commands, and known limitation that
auto-configuration exposes a listener rather than silently wrapping existing
importers.

## Verification order

1. `git diff --check` and status audit; root develop's unrelated
   `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/nativebulk/` remains
   untouched.
2. Core test, then each format/Okio test, then Micrometer and Spring Boot tests.
3. `./gradlew projects` confirms `bluetape4k-graph-io-micrometer` registration;
   verify outgoing variant, BOM/publication metadata, Kover aggregation and
   test-runtime dependency wiring with the relevant Gradle metadata tasks.
4. Targeted compile/test smoke with `--no-daemon --console=plain`; graph database
   Testcontainers are out of scope for this issue.
5. If `.github/workflows/nightly-tests.yml` is changed for graph-io coverage,
   explicitly dispatch it with `scope=full` after local verification and record
   the run URL/result in the receipt and lesson; otherwise record that no Nightly
   workflow mutation was needed.
6. Read Step 4-P performance/stability scan, Step 5 verifier checklist, and
   Step 6-R code-review instructions; run the required Type A six-lens review.
7. Run workflow `completion-check` only after all evidence is read and attach
   implementation/verification/lesson evidence to the run receipt.

## Rollback and scope guard

The change is additive. If a format or optional classpath gate fails, revert only
the affected worktree files; never reset the root develop checkout or remove its
untracked nativebulk path. No backend/Testcontainers, checkpoint/resume, native
loader, PR, merge, issue close, milestone mutation, or release action is included.

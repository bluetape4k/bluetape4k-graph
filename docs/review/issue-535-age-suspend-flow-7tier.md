# #535 AGE suspend JDBC Flow 7-Tier 검토

## 검토 범위와 기준

- 대상 이슈: [#535](https://github.com/bluetape4k/bluetape4k-graph/issues/535)
- 대상 모듈: `graph/graph-age`
- 기준 ref: `fix/issue-535-age-suspend-flow` exact HEAD `7758aa8e430d1460cceaae27667d3636093b178e` (현재 #549 PR #571 exact head `68131d0b06ebad865aa5a00f138dd7dd04066c18` 위에 적층)
- 검토 방식: 구현자와 분리된 `graph_architecture_review`, `graph_code_review` 두 lane의
  source-read-only 검토를 exact HEAD에서 수집하고 현재 검증 결과와 대조했다.
- 관련 기준 문서: `docs/superpowers/specs/issue-535-age-suspend-flow-design.md`,
  `docs/superpowers/plans/issue-535-age-suspend-flow.md`,
  `graph/graph-age/README.md`, `graph/graph-age/README.ko.md`
- 기술 계약: 직접 Flow는 `Dispatchers.IO`의 JDBC cursor, positive
  `PreparedStatement.fetchSize`, channel backpressure, `ResultSet`/transaction close를
  사용한다. streaming transaction은 이미 방출된 prefix를 재시도하지 않도록
  `maxAttempts=1`을 사용하고, transaction scope가 반환한 top-level `Flow`는 commit 전에
  materialize한다.
- 미검증 범위: `executeQuery()` 또는 `ResultSet.next()` 자체가 정지한 경우
  `Statement.cancel()`로 prompt cancellation을 보장하는 계약은 이번 변경에 포함하지
  않는다.

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
|---|---|---|---|
| 1. 계약·호환성 | public API/ABI, backend scope, transaction ownership | `AgeGraphSuspendOperations`의 public constructor/interface/signature 불변; 새 executable·fetch size·attempts는 private; six direct Flow만 `streamQuery`로 수렴 | PASS |
| 2. Kotlin 패턴·API | `$bluetape-kotlin-patterns`, null/error/coroutine 경계, assertions | `Dispatchers.IO`, `withContext`, `trySendBlocking(...).getOrThrow()`, 취소 예외 비삼킴; 새 예외 테스트는 `io.bluetape4k.assertions.assertFailsWith`; 금지 assertion 검색 0건 | PASS |
| 3. Lifecycle·취소 | ResultSet, statement, transaction, collector cancellation/failure | `executeQuery().result.use(transform)`와 Exposed suspended transaction의 close 경계; `first()` 반복 8회 및 collector exception 후 count query 통과; retry prefix 중복은 `maxAttempts=1`로 차단 | PASS; stall cancel은 #552 |
| 4. Concurrency·backpressure | IO blocking, channel capacity, driver prefetch, retry | `channelFlow` + `trySendBlocking`, `DatabaseConfig.defaultFetchSize=8` fixture, fallback 100, 256/128행 회귀; exact fetch/retry fault injection은 #550 | PASS; WATCH |
| 5. Test·fixture | targeted/full integration, deterministic fixture, testcontainers | AGE targeted `29/29`, fresh full `191/191`, `cleanTest --no-build-cache`; explicit `Database`와 default fetch size fixture; Testcontainers는 sequential | PASS |
| 6. 문서·운영 | README locale parity, spec/plan, operational limits | 영문/국문 README에 fetch size·retry·materialize 경계 일치; 설계/계획에 `Statement.cancel()` 미범위 명시; 후속 이슈 #550/#551/#552 | PASS |
| 7. 정적·회귀 | compile, detekt, diff, terminology, source scan | `compileKotlin`, `compileTestKotlin`, `detekt`, `git diff --check` PASS; Korean terminology audit findings 0; direct Flow path의 `MutableList`/`runCatching`/deprecated `sendBlocking` 없음 | PASS |

## 독립 검토 종합

### Architecture lane

- 판정: `PASS`, Architectural Status `WATCH`
- P0/P1: 없음
- 확인: `BlockingExecutable`에서 positive fetch size를 설정하고,
  `DatabaseConfig.defaultFetchSize`가 양수이면 이를 사용하며 아니면 100을 적용한다.
  streaming transaction은 `attempts=1`로 실행해 late `SQLException`의 prefix 중복을
  막는다. Exposed 1.4.0 source의 current/executed statement 및 NonCancellable close
  경계와 `ResultSet.use`의 소유권이 일치한다.
- WATCH: fetch size/retry fault injection 부재(#550), generic nested `Flow` escape
  (#551), JDBC stall의 `Statement.cancel()` 미연계(#552).

### Code lane

- 판정: `PASS`, recommendation `PASS / WATCH`
- 확인 범위: source 변경, six direct Flow call site, tests, README 두 locale, compile/
  detekt/diff evidence, Bluetape assertions 및 public API/ABI.
- P0/P1: 없음. P2 WATCH는 #550의 fetch/retry fault injection, #551의 nested Flow
  escape, #552의 stalled JDBC cancellation이다. merge blocker는 없다.

## Findings와 처분

| 심각도 | 위치·문제 | 처분 |
|---|---|---|
| P0/P1 | 없음. 초기 review에서 발견된 driver fetch-all 위험과 streaming retry prefix 중복은 `execStreaming` 및 `attempts=1`로 수정했다. | 수정 완료, exact HEAD 재검증 |
| P2 | `fetchSize` 값과 late JDBC failure의 단일 시도를 fault injection으로 직접 관찰하지 않는다. | [#550](https://github.com/bluetape4k/bluetape4k-graph/issues/550) 생성 |
| P2 | `suspendTransaction<T>`가 top-level `Flow`만 materialize해 nested `Flow`가 transaction 밖으로 escape할 수 있다. 이는 AGE 단독이 아닌 cross-backend core 계약이다. | [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551) 생성 |
| P2 | `executeQuery()`/`ResultSet.next()` blocking 중 coroutine 취소와 `Statement.cancel()`의 연결을 검증하지 않는다. | [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552) 생성 및 README/spec 제한 명시 |
| P3 | 테스트가 deprecated global-primary constructor를 사용하던 문제. | explicit `AgeGraphSuspendOperations(database, graphName)`로 수정 |

## 검증 증거

- targeted: `./gradlew :bluetape4k-graph-age:cleanTest :bluetape4k-graph-age:test --tests 'io.bluetape4k.graph.age.AgeGraphSuspendOperationsTest' --no-build-cache --no-daemon --console=plain` → `29/29`, `BUILD SUCCESSFUL`.
- module: `./gradlew :bluetape4k-graph-age:cleanTest :bluetape4k-graph-age:test --no-build-cache --no-daemon --console=plain` → `191/191`, failures/errors/skipped `0`, `BUILD SUCCESSFUL`.
- static/build: `:bluetape4k-graph-age:detekt`, `compileKotlin`, `compileTestKotlin` → `BUILD SUCCESSFUL`.
- diff/terms: `git diff --check` PASS; `audit-korean-terms.mjs` 3 files, findings `[]`.
- worktree: exact HEAD `7758aa8e430d1460cceaae27667d3636093b178e`, review 시점 clean.

## 결론

- 최종 7-Tier verdict: `PASS`
- Architectural Status: `WATCH` (비차단 P2 세 건)
- Code lane recommendation: `PASS / WATCH`
- merge blocker: 없음
- #535 구현·검증 범위는 완료했으며, 후속 계약은 #550, #551, #552에서 순서대로
  다룬다. PR/merge/push/issue close는 이 작업에서 수행하지 않는다.

## SPW writer gate

- **SPW-01 — Lock audience, purpose, evidence: PASS.** 대상은 graph-age 유지보수자와
  reviewer이며, 한국어 7-Tier 판정 문서다. exact HEAD, source paths, commands, test
  counts, independent lanes, unknowns(`Statement.cancel()` stall)을 기록했다.
- **SPW-02 — Artifact contract: PASS.** 범위/기준, 7-Tier, 독립 검토, severity별
  finding/disposition, validation, verdict, 후속 이슈를 포함한다.
- **SPW-03 — Korean technical register: PASS.** 기술 용어와 code token을 보존하고
  현재 구현·제안·미범위를 분리했다. `audit-korean-terms.mjs` findings `[]`와
  Korean naturalness checklist(KO-01..KO-07) 검토를 완료했다.
- **SPW-04 — Technical traceability: PASS.** 설계/계획, 구현 commit, 테스트 결과,
  two-lane review, README locale, #550/#551/#552를 finding별 처분에 연결했다.
- **SPW-05 — Read-back: PASS.** 최종 Markdown heading/table/list/code token을
  read-back했고, 아래 lesson과 workflow receipt에 동일한 verdict/evidence를 반영한다.

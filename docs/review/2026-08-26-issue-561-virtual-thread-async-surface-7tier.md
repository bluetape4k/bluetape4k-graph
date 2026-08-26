# #561 Virtual Thread optional async surface 7-Tier 코드 리뷰

## 판정

- 이슈: [#561](https://github.com/bluetape4k/bluetape4k-graph/issues/561)
- 선행 PR: [#585](https://github.com/bluetape4k/bluetape4k-graph/pull/585)
- stacked base: `33d0f0d688e0522286dbc09a57370079d543bc12`
- 구현 범위: `graph-core` Virtual Thread optional merge/schema/transaction/chunked
  surface, capability projection, TinkerGraph TCK, EN/KO 문서
- 판정: **PASS / WATCH**
- 심각도: P0 0, P1 0, P2 4, P3 1

P2는 backend-native cancellation, async materialization의 heap bound, callback
executor 선택, schema capability와 개별 DDL 지원 차이를 후속 위험으로 남긴다.
이 review는 PR merge와 issue close를 승인하지 않으며, 전체 stacked train의 최종
승인 단계까지 병합을 보류한다.

## 수용 기준 추적

| 기준 | 근거 | 상태 |
| --- | --- | --- |
| optional merge/schema/transaction/chunked API가 정의됨 | 네 `GraphVirtualThread*Operations` interface와 repository extension | PASS |
| thread affinity와 executor ownership이 문서화됨 | public KDoc, graph-core EN/KO README, spec | PASS |
| exception/cancellation/timeout 계약이 backend-independent하게 고정됨 | `VirtualThreadOptionalSurfaceTddTest`, README, spec | PASS |
| supported/unsupported capability routing과 adapter 검증 | TinkerGraph facade, marker-hiding decorator, focused adapter TCK | PASS |
| delegate close ownership과 chunk source close | adapter KDoc 및 `collectChunks` finally | PASS |
| Bluetape assertion과 Kotlin pattern 준수 | `io.bluetape4k.assertions.assertFailsWith`, immutable maps, additive interfaces | PASS |
| EN/KO README·KDoc·migration note | `graph/graph-core/README.md`, `README.ko.md`, public KDoc | PASS |

## 7-Tier 결과

| Tier | 검토 내용 | 판정 및 잔여 위험 |
| --- | --- | --- |
| 1. Correctness | merge/schema/transaction/chunked 결과와 chunk 경계, transaction virtual thread affinity | PASS. TinkerGraph supported path와 5개 TCK가 통과했다. |
| 2. API/ABI | 기존 facade와 sync/coroutine API를 유지한 additive optional surface, delegate/surface capability 분리 | PASS. 새 async method는 optional extension/interface이며 기존 public constructor를 바꾸지 않는다. |
| 3. Performance/Boundedness | Bluetape4k virtualFuture helper, one virtual-thread task, chunk source 소비 | WATCH. 반환 list는 전체 materialization하므로 async heap boundedness는 보장하지 않는다. |
| 4. Reliability/Concurrency | exceptional completion의 원인 보존, `cancel(true)`, `orTimeout`, borrowed delegate lifecycle | PASS/WATCH. future 상태와 interrupt 요청은 관찰되지만 JDBC/driver 중단은 backend 책임이다. |
| 5. Security/Resource | unsupported path의 명시적 실패, AutoCloseable chunk source close, delegate close non-ownership | PASS/WATCH. timeout 중 backend connection cancellation과 custom executor 정책은 범위 밖이다. |
| 6. Tests/Observability | TDD RED→GREEN, supported/unsupported/failure/cancel/timeout/focused adapter, full module·Detekt | PASS. container backend는 공통 API 변경이 없어 graph-core/TinkerGraph hosted job으로 추적한다. |
| 7. Documentation/Maintainability | EN/KO README, public KDoc, spec/plan, lesson, WIP, source-to-claim traceability | PASS. `delegateCapabilities()` migration과 materialized chunk 제한을 명시했다. |

## 독립 review disposition

구현 전 RED 테스트는 optional method 부재를 실패로 관찰했다. 구현 후 review에서
P0/P1 결함은 확인되지 않았다. 다음 경계를 의도적으로 유지한다.

1. 통합 facade는 sync marker가 있는 경우에만 `MERGE`, `SCHEMA`, `TRANSACTION`을
   surface capability로 추가한다.
2. marker를 숨긴 decorator는 optional capability를 광고하지 않으며,
   extension 호출은 `UnsupportedOperationException` exceptional future가 된다.
3. `delegateCapabilities()`는 기존 delegate의 전체 mapping을 보존하고,
   `capabilities()`는 호출 가능한 async surface를 표현한다.
4. chunk async API는 source chunk 경계를 보존하지만 future 결과를 materialize한다.

## 검증 영수증

- TDD RED: 구현 전 `VirtualThreadOptionalSurfaceTddTest`가 optional method 부재로
  실패했다.
- TDD GREEN: supported facade, unsupported decorator, focused adapters, failure
  identity, cancellation, timeout을 포함한 5개 TCK가 통과했다.
- graph-core 전체 test: 378개 통과.
- compile: `:bluetape4k-graph-core:compileKotlin` 성공.
- Detekt: `:bluetape4k-graph-core:detekt` 성공.
- 금지 assertion 검색(`assertThrows`, `kotlin.test.assertFailsWith`, `shouldThrow`):
  새 변경 범위 0건.
- `git diff --check`: 통과.
- hosted exact-head CI/Examples receipt는 PR 생성 후 최종 head에서 갱신한다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 graph-core implementation slice를 막는 결함 없음.
- P2: `cancel(true)`와 timeout은 backend JDBC/driver 작업을 중단한다고 보장하지
  않는다. 각 backend의 native cancellation은 별도 issue가 필요하다.
- P2: async chunk 결과는 materialized list이므로 bounded heap API가 아니다.
  `BOUNDED_*`는 source traversal marker와 별도 TCK로 판단한다.
- P2: callback executor를 호출자가 지정하는 별도 surface는 아직 없다.
- P2: `SCHEMA` capability는 schema manager 표면을 뜻하며 TinkerGraph unique
  constraint처럼 개별 DDL이 unsupported일 수 있다.
- P3: 향후 optional method를 추가할 때 interface, capability, unsupported TCK,
  EN/KO KDoc를 같은 train slice에 추가해야 한다.

## 최종 결론

Virtual Thread facade는 Bluetape4k future helper를 사용해 optional sync capability를
명시적인 async surface로 제공하고, unsupported backend·예외·취소·timeout·lifecycle
경계를 TCK와 문서로 고정한다. **PR readiness: PASS / Architecture status: WATCH**.
Hosted exact-head receipt와 전체 stacked train merge는 후속 단계에서 별도 승인한다.

## SPW-01 Source ledger

| 출처 | 사용 목적 |
| --- | --- |
| [#561](https://github.com/bluetape4k/bluetape4k-graph/issues/561) | optional API, TCK, 문서 acceptance |
| [#585](https://github.com/bluetape4k/bluetape4k-graph/pull/585) | stacked base와 선행 train 경계 |
| `GraphVirtualThreadOperations.kt`, `VirtualThreadOperationsAdapter.kt` | 기존 facade와 capability mapping |
| `GraphMergeOperations`, `GraphSchemaManager`, `GraphTransactionalOperations` | sync marker와 delegate contract |
| `CompletableFutureSupport.kt` (bluetape4k core) | `virtualFutureOf`/nullable helper semantics |
| `VirtualThreadOptionalSurfaceTddTest.kt` | supported/unsupported/failure/cancel/timeout 회귀 |
| `README.md`, `README.ko.md`, spec/plan | reader-facing contract와 migration |

## SPW-02 Review contract

7-Tier는 correctness, API/ABI, performance/boundedness, reliability/concurrency,
security/resource, tests/observability, documentation/maintainability를 각각
독립 판정한다. P0/P1은 이슈를 남긴 채 PASS할 수 없으며, P2/P3는 후속 위험으로
분리한다. 정확한 commit과 fresh test output만 최종 증거로 사용한다.

## SPW-03 Korean naturalness checklist

- SPW-01~05와 독자 대상 문장은 한국어로 작성했다.
- `CompletableFuture`, `virtualFutureOf`, `UnsupportedOperationException`, Gradle
  명령과 URL은 계약상 영문 토큰을 보존했다.
- `surface`, `delegate`, `materialize`, `callback affinity`는 API 개념이므로
  주변 한국어 문장에서 의미를 고정했다.
- EN/KO README는 각 locale의 문장 언어를 유지하고 섞지 않았다.

## SPW-04 Source-to-claim traceability

| 주장 | 위치 |
| --- | --- |
| optional capability routing | `VirtualThreadOperationsAdapter.surfaceCapabilities`, `GraphCapabilities.from` |
| unsupported exceptional future | `VirtualThreadOptionalAdapters.kt`, `GraphVirtualThreadOperationsOptionalExt.kt` |
| delegate ownership과 chunk close | `VirtualThreadOptionalAdapters.kt`, `GraphVirtualThreadOperations.kt` |
| thread/timeout/materialization 계약 | 네 optional interface KDoc, EN/KO README |
| 회귀가 통과함 | `VirtualThreadOptionalSurfaceTddTest`, graph-core 전체 test |

## SPW-05 Render/read-back

문서는 파일로 다시 읽어 제목·표·코드 블록·URL을 확인하고 `git diff --check`를
실행한다. 이 checkout에는 `audit-korean-terms.mjs`가 없어 실행하지 못하며, 해당
gap은 PR DoD에 기록한다.

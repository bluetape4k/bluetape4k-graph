# #536 bounded chunk capability 7-Tier review

## 범위와 stacked 기준

- Issue: [#536](https://github.com/bluetape4k/bluetape4k-graph/issues/536)
- Branch: `fix/issue-536-chunk-capability`
- Base: PR #568 live exact head `eda9c433a7004ab91e96e0f8ea8ecade0e1fa68a`
  (`fix/issue-547-catalog-retry-evidence`)
- Scope: graph-core capability/marker, repository fallback, TinkerGraph bounded
  projection, GraphML exporter 문서·KDoc
- Follow-up boundary: #548은 이 branch exact head 위에서 close-aware cursor
  lifecycle을 추가하고, #549는 enum exhaustive consumer compatibility를 별도
  slice로 다룬다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·ABI | PASS | 기존 `CHUNKED_*` enum와 repository 메서드를 유지하고 bounded capability/marker를 additive로 추가했다. graph-core·TinkerGraph·GraphML compile/test와 public enum surface 확인이 통과했다. |
| T2 기능·계약 | PASS | `CHUNKED_*`는 API chunking만, `BOUNDED_CHUNKED_*`는 source bounded 실행만 의미하도록 constructor invariant와 capability fixture를 고정했다. TinkerGraph만 bounded capability를 보고한다. |
| T3 실패·취소 | PASS | 기본 list/Flow fallback과 기존 결과 순서를 유지하고, capability invariant 위반은 `require`로 fail-closed한다. cursor 조기 close/cancellation lifecycle은 #548 scope다. |
| T4 보안·노출 | PASS | 새 credential·URI·runtime dependency를 추가하지 않았고, capability/문서에 운영 secret을 노출하지 않는다. |
| T5 수명주기·동시성 | PASS/WATCH | virtual-thread facade가 delegate capability를 보존하고 TinkerGraph traversal bounded probe가 첫 chunk 소비를 확인한다. public `Sequence.take(1)` close lifecycle은 #548 후속이다. |
| T6 ecosystem·패턴 | PASS | `bluetape4k-assertions`, 기존 repository capability extension, TinkerPop traversal iterator, virtual-thread delegate projection을 재사용했다. 네 remote backend에 증명되지 않은 bounded marker를 추가하지 않았다. |
| T7 문서·인계 | PASS/WATCH | root·graph-core·GraphML EN/KO README, lesson, plan/design, CHANGELOG, WIP를 갱신했다. PR #536 생성·hosted checks와 최종 train merge는 후속 gate다. |

## 검증 증거

- graph-core test: 351 tests — PASS.
- graph-tinkerpop test: 114 tests — PASS.
- graph-io-graphml test: 44 tests — PASS.
- affected Detekt: graph-core, graph-tinkerpop, graph-io-graphml — PASS.
- Backend conformance: AGE → Neo4j → Memgraph → FalkorDB를 순차 실행했고
  각 capability conformance 4 tests가 PASS했다.
- `git diff --check` — PASS.
- `bluetape4k-assertions` assertion audit와 금지된 JUnit/Kotlin exception
  assertion 신규 도입 없음 — PASS.

## DoD Status

- [x] API chunking과 backend bounded 실행을 별도 capability/constraint로 분리했다.
- [x] TinkerGraph만 bounded capability를 광고하고 네 remote backend는 API
  chunking fallback으로 남겼다.
- [x] core·TinkerGraph·GraphML affected tests, Detekt, 순차 conformance를
  통과했다.
- [x] root/graph-core/GraphML EN/KO 문서와 lesson/CHANGELOG/WIP를 기록했다.
- [x] #548 close-aware lifecycle과 #549 enum compatibility를 별도 후속 범위로
  연결했다.
- [ ] PR #536 hosted exact-head review/checks와 전체 train merge — 마지막 승인
  단계에서 수행한다.

최종 판정: **PASS/WATCH**. #547 exact head 위의 #536 로컬 구현·검증은
완료되었고, PR 생성 후 hosted evidence를 추가한다. 병합은 전체 train의 마지막
사용자 승인 전까지 보류한다.

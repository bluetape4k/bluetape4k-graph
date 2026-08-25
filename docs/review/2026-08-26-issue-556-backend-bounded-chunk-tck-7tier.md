# #556 backend bounded chunk 및 기준 데이터 변경 TCK 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#556](https://github.com/bluetape4k/bluetape4k-graph/issues/556)
- 선행 PR: [#580](https://github.com/bluetape4k/bluetape4k-graph/pull/580)의
  exact head `31c959c984f0cbee3666283392491b646c8e0e99`
- branch: `test/issue-556-backend-bounded-chunk-stacked`
- 대상: `graph-core`, `graph-io-core`의 fallback contract와 CSV/GraphML
  sync/suspend exporter TCK
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; hosted receipt는 PR 생성 후
  exact head에서 갱신한다)
- WATCH: fake backend가 검증하는 것은 첫 stage 이후 exporter가 다시 조회하지
  않는다는 기준 데이터 계약이다. 실제 backend transaction snapshot이나
  process 간 일관성을 주장하지 않는다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·범위·선행 base | live #556, #580 exact head, #539 export spool source와 #536 capability contract | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | `io.bluetape4k.assertions` matcher와 `assertFailsWith`, 기존 graph test fixture·delegate seam 재사용 | PASS |
| SPW-03 | chunk 호출 계약 | CSV/GraphML 네 경로가 chunk size `1`과 `vertices:1`·`edges:1` 요청을 직접 관찰 | PASS |
| SPW-04 | 기준 데이터·수명주기 | 첫 chunk stage 뒤 fake backend map을 `before`에서 `after`로 바꾸고 output에 `before`만 남는지 검증 | PASS / WATCH |
| SPW-05 | fallback boundedness | sync list와 suspend Flow 기본 fallback이 첫 emission 전에 label 전체를 한 번 materialize하는 회귀를 고정 | PASS |
| SPW-06 | 문서·호환성 | root/backend capability matrix, graph-io-core·CSV·GraphML EN/KO README, spec·plan 갱신 | PASS |
| SPW-07 | 운영·추적성 | local test/Detekt/diff/assertion scan 완료; PR exact-head CI·Examples receipt는 hosted cycle 후 갱신 | PENDING → PASS 예정 |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. 요구사항·범위 | #556 수용 기준과 #539 exporter snapshot, #536 capability 범위를 혼동하지 않는가 | PASS. 네 exporter의 재조회 방지와 fallback 문서화를 분리하고 #469/#471의 종료된 list/chunk 범위와 중복하지 않는다. |
| 2. API/ABI | public API/ABI를 불필요하게 바꾸는가 | PASS. production signature와 backend interface를 변경하지 않고 기존 chunk API와 capability 문장만 검증한다. |
| 3. Kotlin/Bluetape 패턴 | null safety·불변 결과·프로젝트 assertion을 지키는가 | PASS. 기존 `GraphElementId`/model과 delegate를 사용하고 `shouldBeEqualTo`, `shouldContain`, `shouldNotContain`, `assertFailsWith`를 사용한다. mutable map은 backend mutation을 재현하는 테스트 seam에만 한정한다. |
| 4. 동시성·boundedness | 요청 chunk와 기준 데이터 수명이 정확히 관찰되는가 | PASS / WATCH. chunk-aware fake는 요청 크기와 호출 횟수를 확인한다. fallback은 API chunking일 뿐 source bounded 실행이 아니며, DB transaction snapshot을 보장한다고 확대하지 않는다. |
| 5. 오류·수명주기 | stage/replay 경계에서 중복 조회나 데이터 혼입이 생기는가 | PASS. #539의 single-stage spool/replay를 그대로 사용하고 첫 chunk 이후 mutation을 output에 섞지 않는 sync/suspend 회귀를 추가했다. |
| 6. 테스트·관측성 | 실패 모드와 운영 계약을 재현하는가 | PASS. `graph-core 357`, `graph-io-core 158`, `CSV 55`, `GraphML 48`이 모두 `0 failures / 0 errors / 0 skipped`이며 Detekt와 금지 assertion scan도 통과했다. |
| 7. 문서·유지보수 | capability 차이와 후속 위험이 추적 가능한가 | PASS / WATCH. EN/KO README와 설계 문서에 TinkerGraph bounded cursor와 AGE/Neo4j/Memgraph/FalkorDB fallback 경계를 적었고, 실제 backend conformance와 transaction snapshot은 후속 검증 범위로 남겼다. |

## 검증 영수증

- full local: `:bluetape4k-graph-core:test :bluetape4k-graph-io-core:test
  :bluetape4k-graph-io-csv:test :bluetape4k-graph-io-graphml:test`
  `BUILD SUCCESSFUL in 28s`
- local test totals: graph-core `357/357`, graph-io-core `158/158`, CSV
  `55/55`, GraphML `48/48`; failures/errors/skipped 모두 `0`
- 신규 mutation TCK: CSV sync/suspend, GraphML sync/suspend 각 PASS; core sync/suspend
  default fallback eager-materialization 각 PASS
- static: 세 대상 모듈 Detekt `BUILD SUCCESSFUL`
- 금지 assertion scan: `assertThrows`, `kotlin.test.assertFailsWith`, `shouldThrow`,
  `invoking {` `0 matches`
- `git diff --check`: PASS
- Korean audit helper: checkout에 `scripts/audit-korean-terms.mjs`가 없어 실행하지
  못한 기존 환경 gap이다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 TCK·문서 slice를 막는 결함 없음
- P2: AGE/Neo4j/Memgraph/FalkorDB가 실제 bounded chunk/cursor를 제공하는지는
  backend별 conformance에서 별도로 확인해야 한다.
- P2: fallback list/Flow의 전체 materialization peak와 DB transaction-consistent
  snapshot은 이 PR의 계약이 아니다.
- P3: backend별 bounded implementation을 추가할 때 동일 TCK를 실제 container
  adapter에 연결하고 capability matrix를 다시 갱신해야 한다.

## 최종 결론

네 exporter 경로의 첫 stage 기준 데이터 보존과 chunk 호출 관찰을 공통 TCK로
고정하고, 기본 fallback의 전체 materialization 가능성을 API 문서와 graph-core
회귀로 명시했다. **PR readiness: PASS / Architecture status: WATCH**.
최종 hosted exact-head receipt와 전체 train merge는 마지막 승인 단계에서만
진행한다.

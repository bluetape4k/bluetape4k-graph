# #554 GraphImportJobStateStore durable contract TCK 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#554](https://github.com/bluetape4k/bluetape4k-graph/issues/554)
- 대상: `graph-io-core`의 reusable `testFixtures` TCK와 state-store contract KDoc
- stacked base: PR [#577](https://github.com/bluetape4k/bluetape4k-graph/pull/577)의
  live exact head `c3ac327a23730b977c5ffc03d730b0fc8abecdcd`
- branch: `fix/issue-554-state-store-tck-stacked`
- implementation commit: `98dddf35`
- 판정: **PASS / WATCH** (P0/P1 blocker 없음)
- WATCH: 실제 durable adapter가 아직 없으므로 CAS/transaction 운영 성공을 이
  slice가 증명하지 않는다. adapter 구현 시 retry harness를 연결해 이 TCK를
  실행해야 한다. 기본 store monitor 병렬성은 [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)로 분리한다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·범위·선행 base | live #554, #577 exact base, `GraphImportJobStateStore` source | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | immutable report, `io.bluetape4k.assertions.assertFailsWith`·`shouldBeEqualTo`·`shouldNotBeNull`·`shouldBeNull` | PASS |
| SPW-03 | retry·저장 경계 | 최신 report 재평가, intervening report 주입, mismatch 시 save invocation 불변 | PASS |
| SPW-04 | 테스트·정적 검증 | TCK targeted/full, Detekt, 금지 assertion scan, `git diff --check` | PASS |
| SPW-05 | 문서·운영 증거 | README EN/KO, 설계·lesson, WIP, PR exact-head hosted receipt | PASS |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. API/ABI | production signature와 report serialization을 깨뜨리는가 | PASS. `GraphImportJobStateStore` method와 data class ABI를 유지하고 KDoc만 보강 |
| 2. Kotlin/Bluetape 패턴 | 불변성·null safety·의도 matcher를 지키는가 | PASS. `copy`, `shouldNotBeNull`, `assertFailsWith`, shared test fixture를 사용 |
| 3. 상태·동시성 | stale transition을 버리고 최신 report로 retry하는가 | PASS. retry harness가 intervening report를 저장한 뒤 transform을 재평가하고 첫 결과를 저장하지 않음 |
| 4. 오류·계약 | mismatch·transform 실패가 state를 오염시키는가 | PASS. mismatch는 save invocation 없이 실패하고 transform 실패 후 기존 report를 재조회 |
| 5. 테스트 | 기본 구현과 future durable adapter가 같은 TCK를 재사용하는가 | PASS. `java-test-fixtures` variant, project/external Gradle 소비 예시와 기본 in-memory reference harness 제공 |
| 6. 문서·호환성 | durable override 경계와 retry-safe 규칙이 reader-facing 문서와 일치하는가 | PASS. KDoc 및 README EN/KO에 동일 계약 기록 |
| 7. 운영·유지보수 | exact receipt와 후속 위험이 추적 가능한가 | WATCH. hosted PR checks는 exact head에서 확인하며, 실제 durable backend 운영 검증은 후속 scope |

## 독립 리뷰 결론

- P0=0, P1=0: 현재 TCK/documentation slice를 막는 결함 없음
- P2 WATCH: concrete durable adapter와 multi-process contention은 저장소에 없어
  이 PR에서 허위 green으로 주장하지 않음
- P3: 기본 store 전체 monitor 범위는 [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)에서 별도 측정
- N/A: suspend state-store counterpart와 production durable caller는 source 검색에
  없어 API를 추가하지 않음

## 검증 영수증

- targeted TCK: `InMemoryGraphImportJobStateStoreContractTest` 6/6 PASS
- full: `:bluetape4k-graph-io-core:test` `SUCCESS: Executed 149 tests`
- `:bluetape4k-graph-io-core:detekt`: PASS (`BUILD SUCCESSFUL`)
- 금지 assertion scan: `assertThrows`, `kotlin.test.assertFailsWith`, `shouldThrow`, `invoking {` 없음
- `git diff --check`: PASS
- `SUSPEND_COUNTERPART_MATCHES=0`: suspend state-store counterpart 없음
- implementation/docs commit과 PR exact base/head, hosted CI·Examples run URL/result는
  PR 생성 후 최신 lifecycle receipt로 갱신

## 최종 결론

공통 TCK는 in-memory 기본 store에서 update invariant와 CAS retry 관찰 경계를
실행하고, future durable adapter가 같은 test fixture를 소비할 수 있는 명확한
handoff를 남긴다. **PR readiness: PASS / Architecture status: WATCH**. 전체
stacked train merge는 마지막 일괄 승인 단계에서만 수행한다.

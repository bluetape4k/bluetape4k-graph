# #557 spool record serialization peak memory·constructor cleanup 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#557](https://github.com/bluetape4k/bluetape4k-graph/issues/557)
- 선행 PR: [#581](https://github.com/bluetape4k/bluetape4k-graph/pull/581)
- stacked base: `test/issue-556-backend-bounded-chunk-stacked`
  exact head `534aed0111d062450d5d6a3958d3cb0294e34bba`
- 대상: `graph-io-core`의 `GraphIoRecordSpool`과 CSV/GraphML lifecycle 문서
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; hosted receipt는 PR exact head에서
  갱신한다)
- WATCH: capped heap payload는 한 레코드 단위의 bounded allocation을 의미한다.
  전체 export의 backend boundedness나 transaction-consistent snapshot은 이
  slice가 주장하지 않는다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·선행 base | live #557, PR #581, #556 exact head, #539 spool source | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | private resource factory, capped buffer, `io.bluetape4k.assertions.assertFailsWith`/matcher | PASS |
| SPW-03 | serialization peak | capped buffer가 max 초과 시 즉시 실패하고 direct `writeTo`가 second `toByteArray`를 피함 | PASS |
| SPW-04 | constructor lifecycle | 두 번째 temp file/output 실패 시 앞서 만든 stream close와 모든 file delete를 fault injection으로 확인 | PASS |
| SPW-05 | compatibility | public no-arg constructor, length-prefix format, replay/property-key 순서 유지 | PASS |
| SPW-06 | 문서·운영 경계 | graph-io core/CSV/GraphML EN·KO README, spec·plan·lesson·WIP | PASS |
| SPW-07 | hosted traceability | local receipt 완료; PR exact-head CI·Examples terminal receipt는 hosted cycle 후 갱신 | PENDING → PASS 예정 |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. 요구사항·범위 | #557의 peak/constructor 계약이 #539 lifecycle과 분리되어 있는가 | PASS. serializer·constructor resource만 다루고 exporter API와 backend bounded capability는 건드리지 않는다. |
| 2. API/ABI | 공개 생성자와 spool record format이 깨지는가 | PASS. public no-arg constructor, length prefix, replay format을 유지하고 injection constructor는 internal이다. |
| 3. Kotlin/Bluetape 패턴 | 타입·불변성·assertion 사용이 일관적인가 | PASS. null resource는 construction scope에서만 nullable로 관리하고 정상 object fields는 non-null이며 Bluetape assertion을 사용한다. |
| 4. 메모리·동시성 | record peak와 replay lifecycle이 bounded하게 유지되는가 | PASS / WATCH. capped payload 한 개와 direct write를 사용한다. replay input 동시 close는 기존 synchronized set을 유지하며 전체 backend snapshot은 주장하지 않는다. |
| 5. 오류·수명주기 | oversize·constructor failure가 partial output/orphan을 남기는가 | PASS. max 초과 전 partial length prefix를 쓰지 않고, initialization 실패에서 primary 예외와 cleanup을 분리한다. |
| 6. 테스트·관측성 | 회귀가 실제 실패 모드를 재현하는가 | PASS. no-copy seam, small max guard, second file/output fault injection과 기존 replay/close tests를 실행했다. |
| 7. 문서·유지보수 | 제한과 후속 위험이 reader-facing 문서에 있는가 | PASS / WATCH. 128 MiB cap·fail-clean setup·fallback boundedness 경계를 EN/KO README에 기록하고 hosted receipt를 후속 갱신한다. |

## 검증 영수증

- TDD RED: 기존 class에 hardening constructor parameter가 없어
  `compileTestKotlin`이 `No parameter with name 'maxRecordBytes'`로 실패
- targeted GREEN: `GraphIoRecordSpoolTest` `8/8 PASS`
- 신규 회귀: no-second-copy, oversized partial-write 방지, second temp file 실패,
  second output 실패가 모두 PASS
- 기존 replay/property order, finish/close, abandoned replay input 회귀도 PASS
- full graph-io-core test, Detekt, 금지 assertion scan, diff-check와 hosted
  exact-head receipt는 PR 생성 후 완료한다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 implementation slice를 막는 결함 없음
- P2: one-record cap은 전체 export heap bound 또는 disk quota를 의미하지 않는다.
- P2: 실제 backend transaction snapshot과 process 간 resource ownership은 이 PR의
  범위가 아니다.
- P3: payload format을 변경하거나 다른 serializer를 추가할 때 동일한 max/copy
  contract와 cleanup fault injection을 재사용해야 한다.

## 최종 결론

spool writer가 record payload를 한 번만 보유하고 직접 기록하도록 하며, constructor
중간 실패에서 이미 만든 임시 resource를 fail-clean하게 정리한다. **PR readiness:
PASS / Architecture status: WATCH**. 최종 hosted exact-head receipt와 전체 train
merge는 마지막 승인 단계에서만 진행한다.

# #558 suspend replay cancellation checkpoint 및 output lifecycle 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#558](https://github.com/bluetape4k/bluetape4k-graph/issues/558)
- 선행 PR: [#582](https://github.com/bluetape4k/bluetape4k-graph/pull/582)
- stacked base: `fix/issue-557-spool-peak-cleanup-stacked`
  exact head `5d5cd3f64bea1aedd7df66f64ca33a739970353c`
- 대상: CSV/GraphML suspend exporter replay와 output lifecycle TCK
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; hosted receipt는 PR exact head에서
  갱신한다)
- WATCH: record checkpoint는 blocking writer 호출을 중단시키는 interrupt 보장이
  아니라, 각 record 사이에서 cancellation을 관찰하는 bounded checkpoint다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·선행 base | live #558, PR #582, #557 exact head, #539 suspend spool source | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | immutable spool replay, `currentCoroutineContext().ensureActive()`, Bluetape assertions | PASS |
| SPW-03 | cancellation checkpoint | CSV/GraphML large fake sink가 첫 record 취소 후 후속 record를 쓰지 않음 | PASS |
| SPW-04 | output ownership | `closeOutput=false`는 open, `true`는 close를 CSV/GraphML TCK로 확인 | PASS |
| SPW-05 | primary/suppressed | direct `CancellationException`와 owned close failure에서 primary identity 및 suppressed를 확인 | PASS |
| SPW-06 | compatibility | sync/virtual-thread 경로, spool format, immutable stage-time 값 유지 | PASS |
| SPW-07 | hosted traceability | local receipt 완료; PR exact-head CI/Examples terminal receipt는 hosted cycle 후 갱신 | PENDING → PASS 예정 |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. 요구사항·범위 | suspend replay checkpoint와 output lifecycle만 다루며 #539 snapshot 경계를 보존하는가 | PASS. backend API나 spool format을 변경하지 않는다. |
| 2. API/ABI | public exporter/sink API와 ownership 의미가 깨지는가 | PASS. 내부 cleanup 구현만 변경하고 `OutputStreamSink` 계약은 TCK로 고정한다. |
| 3. Kotlin/Bluetape 패턴 | coroutine context, nullability, assertions가 일관적인가 | PASS. context checkpoint와 `NonCancellable` cleanup을 사용하고 테스트는 `io.bluetape4k.assertions.assertFailsWith`를 사용한다. |
| 4. 메모리·동시성 | replay가 immutable spool을 재사용하고 cancellation이 bounded하게 관찰되는가 | PASS / WATCH. sequence record 경계를 확인하지만 blocking write 자체를 interrupt한다고 주장하지 않는다. |
| 5. 오류·수명주기 | source/sink/cancellation primary와 cleanup suppressed가 유지되는가 | PASS. writer/session/output/spool을 모두 시도하고 primary identity를 outer catch에서 보존한다. |
| 6. 테스트·관측성 | 실제 실패 모드와 sink ownership을 재현하는가 | PASS. 큰 fake record, job cancellation, direct cancellation, caller-owned/owned sink, close failure TCK가 통과한다. |
| 7. 문서·유지보수 | 제한, fallback, hosted gap이 문서와 receipt에 기록되는가 | PASS / WATCH. EN/KO README와 spec/plan/lesson/WIP에 기록하고 hosted dispatch 입력 결함은 별도 receipt로 남긴다. |

## 검증 영수증

- TDD RED: checkpoint가 없을 때 GraphML이 cancellation 뒤에도 `row-3` record를
  출력하는 실패를 확인했다.
- targeted GREEN: CSV/GraphML replay cancellation 및 close-failure TCK 통과
- suspend class GREEN: CSV 10개, GraphML 9개 테스트 통과
- 후속: graph-io CSV/GraphML 전체 test, Detekt, 금지 assertion scan,
  `git diff --check`, hosted exact-head CI/Examples receipt를 PR 생성 후 갱신한다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 implementation slice를 막는 결함 없음
- P2: record checkpoint는 non-interruptible blocking write를 중단하지 않으며
  checkpoint 사이의 지연은 sink/backend 구현에 좌우된다.
- P2: compatibility fallback의 source full materialization과 backend transaction
  snapshot은 이 PR 범위가 아니다.
- P3: 새로운 exporter format은 동일한 ownership·primary/suppressed·cancellation
  TCK를 재사용해야 한다.

## 최종 결론

CSV/GraphML suspend replay가 record 경계에서 cancellation을 관찰하고,
caller-owned/owned sink 및 cleanup exception 우선순위를 명시적으로 보장한다.
**PR readiness: PASS / Architecture status: WATCH**. 최종 hosted exact-head
receipt와 전체 train merge는 마지막 승인 단계에서만 진행한다.

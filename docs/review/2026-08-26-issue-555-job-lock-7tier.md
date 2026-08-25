# #555 GraphImportJobStateStore job별 lock 범위 최적화 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)
- 대상: `graph-io-core`의 `InMemoryGraphImportJobStateStore`
- stacked base: PR [#578](https://github.com/bluetape4k/bluetape4k-graph/pull/578)의
  exact head `26b41485d3107a99a555678de85fb455b1000504`
- branch: `fix/issue-555-state-store-job-lock-stacked`
- implementation commit: `030f7879`
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; final head hosted PASS)
- WATCH: lock 최적화는 JVM-local reference store에 한정한다. 실제 durable
  adapter의 process 간 contention·transaction rollback·정량 성능은 이 PR이
  증명하지 않는다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·범위·선행 base | live #555, #578 exact base, source inventory | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | `ConcurrentHashMap`, `ReentrantLock`, immutable report, Bluetape assertions | PASS |
| SPW-03 | lock 범위 | 서로 다른 job overlap, 같은 job serialization, direct access 공통 lock | PASS |
| SPW-04 | lifecycle/cancellation | 참조 카운트 감소·idle entry 제거, interruptible waiter 회귀 test | PASS |
| SPW-05 | 테스트·정적 검증 | targeted/full, Detekt, 금지 assertion scan, `git diff --check` | PASS |
| SPW-06 | 문서·호환성 | KDoc, README EN/KO, 설계·lesson·WIP | PASS |
| SPW-07 | hosted 운영 증거 | candidate head CI `32899855369`·Examples `32899855380`, final head CI `32900966027`·Examples `32900966048` terminal PASS; PR metadata exact-head read-back | PASS |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. API/ABI | public method·report serialization을 깨뜨리는가 | PASS. interface와 data class signature를 유지하고 in-memory override만 추가 |
| 2. Kotlin/Bluetape 패턴 | null safety·불변성·의도 matcher를 지키는가 | PASS. `ConcurrentHashMap`, immutable `copy`, `shouldNotBeNull`, `shouldBeTrue`, `assertFailsWith` 사용 |
| 3. 상태·동시성 | job 간 병렬성과 job 내부 순서를 동시에 보장하는가 | PASS. key별 reentrant lock과 map을 함께 사용하고 deterministic latch test로 관찰 |
| 4. 오류·취소 | transform 실패나 interrupt가 state/lock을 오염시키는가 | PASS. acquire flag와 `finally` cleanup, interrupted waiter 후 후속 update 성공 |
| 5. 테스트 | 회귀가 실패 모드까지 재현하는가 | PASS. overlap·serialization·cancel 3개 test와 기존 8개 TCK 유지 |
| 6. 문서·호환성 | JVM-local/durable 경계와 수용 기준이 일치하는가 | PASS. KDoc·README EN/KO·spec·lesson에 동일 범위 기록 |
| 7. 운영·유지보수 | exact receipt와 미검증 위험이 추적 가능한가 | PASS/WATCH. final head hosted receipt와 exact base/head·metadata를 read-back했고 durable multi-process·정량 성능은 후속 범위로 남겼다 |

## 검증 영수증

- targeted: `InMemoryGraphImportJobStateStoreConcurrencyTest` 3/3 PASS
- full: `:bluetape4k-graph-io-core:test` `SUCCESS: Executed 154 tests`
- static: `:bluetape4k-graph-io-core:detekt` PASS (`BUILD SUCCESSFUL`)
- 금지 assertion scan: `assertThrows`, `kotlin.test.assertFailsWith`, `shouldThrow`, `invoking {` 없음
- `git diff --check`: PASS
- hosted candidate head `5260dc89f16af9503c5c9d1b4016c3c14ceab9ea`: CI
  [32899855369](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32899855369)
  와 Examples
  [32899855380](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32899855380)이
  모두 terminal PASS
- final head `419e1e4ccbe7063514cccabce4fb505efaf538ae`: CI
  [32900966027](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32900966027)
  와 Examples
  [32900966048](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32900966048)이
  모두 terminal PASS; PR #579의 exact base/head와 `MERGEABLE`/`CLEAN` 상태를
  read-back했다.

## 최종 결론

in-memory reference store의 전체 monitor를 job별 interruptible lock으로 좁혀
job 간 head-of-line blocking을 제거하면서 동일 job atomic transition과
cancellation cleanup을 유지한다. **PR readiness: PASS / Architecture status:
WATCH**. 전체 stacked train merge는 마지막 일괄 승인 단계에서만 수행한다.

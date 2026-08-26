# #560 Serializable option invariant TCK 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#560](https://github.com/bluetape4k/bluetape4k-graph/issues/560)
- 선행 PR: [#584](https://github.com/bluetape4k/bluetape4k-graph/pull/584)
- stacked base: PR #584 current head `ab9753ca3748cf723675b887f3d9b9c4eebe8d7a`
- 대상: `graph-core`의 Serializable traversal/algorithm options와
  `MissingWeightPolicy.UseDefault`
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; exact-head hosted receipt의 전역 CI는
  기존 benchmark 실패와 workflow_dispatch 입력 결함으로 부분 통과)
- WATCH: Java serialization은 신뢰 경계가 아니므로 이 변경은 invariant 복구만
  담당하며, untrusted stream 허용 여부는 `ObjectInputFilter` 정책이 결정한다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·선행 base | live #560, PR #584, exact head, option source inventory | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | immutable data class state, explicit null checks, `io.bluetape4k.assertions` | PASS |
| SPW-03 | 생성자 invariant | Neighbor depth, PageRank tolerance, Component minSize와 기존 positive/range guard | PASS |
| SPW-04 | serialization invariant | concrete `readObject`가 `defaultReadObject` 뒤 동일 조건을 재검사 | PASS |
| SPW-05 | nested policy | `UseDefault`의 finite/positive weight guard가 PathOptions 안에서도 실행 | PASS |
| SPW-06 | compatibility | public properties/constructors와 `serialVersionUID = 1L` 유지 | PASS |
| SPW-07 | hosted traceability | PR #585 exact head `29133f1dce97eca728b28c8d069c7f5e00589b5b`와 CI/Examples terminal receipt를 기록 | PASS / WATCH |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. 요구사항·범위 | Java deserialization이 우회하는 option invariant를 graph-core 범위에서 복구하는가 | PASS. #560의 일곱 concrete option과 중첩 `UseDefault`를 포함한다. |
| 2. API/ABI | public signature, default, 기존 serialization identity를 깨뜨리는가 | PASS. public API는 유지하고 모든 concrete descriptor의 UID를 `1L`로 고정한다. |
| 3. Kotlin/Bluetape 패턴 | nullability와 assertion 사용이 프로젝트 규칙에 맞는가 | PASS. production은 명시적 invariant helper를 사용하고 TCK는 `assertFailsWith`, `shouldContain`, `shouldBeEqualTo`를 사용한다. |
| 4. 오류·보안 | malformed stream이 정상 객체로 유입되지 않고 원인 메시지를 보존하는가 | PASS / WATCH. `InvalidObjectException`에 필드·값을 포함하며, stream filtering 자체는 호출자 책임이다. |
| 5. 동시성·수명주기 | readObject가 외부 상태나 backend lifecycle을 변경하는가 | PASS. 입력 객체의 기본 필드만 검사하고 graph/backend resource를 열지 않는다. |
| 6. 테스트·관측성 | 정상 round-trip, forged payload, nested failure, UID를 재현하는가 | PASS. Unsafe test-only payload forge로 constructor bypass를 재현하고 7개 concrete option을 모두 확인한다. |
| 7. 문서·유지보수 | EN/KO README와 review/lesson/WIP가 계약과 신뢰 경계를 설명하는가 | PASS / WATCH. local·hosted receipt와 workflow/benchmark 제한을 함께 기록했다. |

## 검증 영수증

- TDD RED: constructor를 거치지 않은 malformed serialized payload 3건이
  `InvalidObjectException` 없이 복원되는 실패를 관찰했다.
- TDD GREEN: `GraphOptionsSerializationTest` round-trip, forged payload,
  nested `UseDefault`, null field, `serialVersionUID` 검증을 추가했다.
- Targeted verification: `GraphOptionsSerializationTest`,
  `GraphTraversalOptionsTest`, `GraphAlgorithmOptionsTest` 47개 테스트 통과.
- Exception tests는 모두 `io.bluetape4k.assertions.assertFailsWith`를 사용하며
  새 JUnit/Kotlin assertion 금지 목록을 도입하지 않는다.
- ABI 확인은 public constructor/property surface와 private `readObject`를
  `javap`/`ObjectStreamClass`로 확인한다.
- Full graph-core test, Detekt, diff-check, exact-head hosted receipt는 PR 생성
  직전과 hosted cycle 후에 갱신한다.
- Hosted exact-head receipt: PR #585 head
  `29133f1dce97eca728b28c8d069c7f5e00589b5b`에서 Examples run
  [`32918378892`](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32918378892)은
  terminal success이며, CI run
  [`32918376820`](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/32918376820)은
  core/backend·Build/Detekt를 통과했다. 전역 CI는 (1) workflow_dispatch에서
  `github.event.before`가 비어 Image Family Gate의 `git diff`가 exit 128,
  (2) `graph-benchmark`의 `WeightedShortestPathBenchTest`가
  `WeightedShortestPathBench.kt:85`에서 `Required value was null`로 실패해
  `failure`였다. 같은 테스트를 선행 #559 head
  `ab9753ca3748cf723675b887f3d9b9c4eebe8d7a`에서 로컬 재실행해 동일 실패를
  확인했으므로 #560 변경의 회귀로 분류하지 않는다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 implementation slice를 막는 결함 없음.
- P2: Java serialization 자체는 임의 클래스 인스턴스화와 gadget 위험을 제거하지
  않으므로, 외부 입력에는 `ObjectInputFilter`와 별도 transport 정책이 필요하다.
- P2: `readObject` 메시지는 현재 constructor 메시지와 맞추지만, 향후 public
  invariant를 변경할 때 constructor와 deserialization 검사를 함께 수정해야 한다.
- P3: 새 Serializable option을 추가하면 `serialVersionUID`, round-trip, forged
  payload TCK와 EN/KO 문서를 같은 train slice에 추가해야 한다.

## 최종 결론

graph-core Serializable option은 정상 round-trip에서 public state와 ABI identity를
보존하고, constructor bypass payload에서도 동일 invariant를 `InvalidObjectException`으로
거부한다. **PR readiness: PASS / Architecture status: WATCH**. PR #585의 Examples와
핵심 graph/backend CI는 exact head에서 통과했지만, 전역 CI에는 위 두 가지 비코드성
제한이 남아 있다. 전체 stacked train merge는 마지막 승인 단계에서만 진행한다.

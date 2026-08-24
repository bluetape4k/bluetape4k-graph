# Issue #540 graph-core option validation 7-Tier 리뷰

## 범위와 기준

`GraphTraversalOptions`와 `GraphAlgorithmOptions`의 public constructor가
backend query를 만들기 전에 입력 범위를 거부하는지 검토했다. 기준 이슈는
[#540](https://github.com/bluetape4k/bluetape4k-graph/issues/540)이며,
`develop` 기준선에서 `GraphTraversalOptions.kt`, `GraphAlgorithmOptions.kt`,
두 option 테스트 파일, 영문·한국어 `graph-core` README를 확인했다.

## 7-Tier 결과

| Tier | 판정 | 근거 |
| --- | --- | --- |
| T1 계약·범위 | PASS | neighbor `maxDepth=0`은 이웃 미확장, path `maxDepth=0`은 vertex-only path, BFS/DFS `maxDepth=0`은 시작 정점 전용으로 허용한다. cycle depth와 방문·반복·결과·사이클·컴포넌트 한도는 양수이며 PageRank 실수값은 유한 범위다. |
| T2 API·ABI | PASS | data class와 `Serializable`/`serialVersionUID`를 유지하고 invalid 입력만 조기 거부한다. 기존 유효한 zero-depth 호출과 기본값을 보존한다. |
| T3 구현·패턴 | PASS | 정수 경계에는 `requireZeroOrPositiveNumber`/`requirePositiveNumber`를 사용하고, 실수 경계에는 Bluetape `requireFinite`/`requireInRange`/`requirePositiveNumber`를 조합했다. |
| T4 테스트 | PASS | `bluetape4k.assertions.assertFailsWith`로 음수·0·`NaN`·무한대 경계를 고정하고, neighbor/path/BFS/DFS zero-depth 의미와 cycle zero-depth 거부를 회귀로 고정했다. |
| T5 backend 영향 | PASS / WATCH | graph-core model·runner·serialization 회귀가 통과했으며, backend query를 만들기 전 공통 option 경계에서 invalid 값이 차단된다. 실제 backend conformance는 별도 gate다. |
| T6 문서·사용성 | PASS | 영문·한국어 README에 constructor validation, 허용 범위, `IllegalArgumentException` 계약을 함께 기록했다. |
| T7 검증·운영 | PASS / WATCH | compile·detekt·diff-check가 통과했다. 실제 외부 graph database 컨테이너 행렬은 이 순수 model 변경의 범위 밖이며 후속 conformance에서 재확인한다. |

## 구현 근거

- `GraphTraversalOptions.kt` — `NeighborOptions`·`PathOptions`·`BfsDfsOptions`는
  zero-depth 의미를 보존하는 `requireZeroOrPositiveNumber`를, `CycleOptions`와
  양수 한도는 `requirePositiveNumber`를 사용한다.
- `GraphAlgorithmOptions.kt` — PageRank의 `iterations`, `topK`,
  `dampingFactor`, `tolerance`를 각각 양수·범위·유한성 계약으로 검증한다.
- `GraphAlgorithmOptions.kt` — `ComponentOptions.minSize`를 양수로 제한한다.
- graph-core option/runner/serialization tests — 경계값별 `assertFailsWith` 회귀와
  zero-depth 실행·직렬화 의미를 확인한다.

## 잔여 위험과 후속 범위

- P0/P1: cycle zero-depth와 invalid limit은 생성·역직렬화 시 차단된다.
- P2: backend 고유 native query가 option 값을 별도로 변환하는 경우의
  cross-backend conformance와 weighted path depth 적용은 컨테이너 행렬에서
  후속 확인한다([#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559)).
- P2: Java serialization이 constructor invariant를 우회하지 않는지 round-trip과
  invalid payload 정책을 별도 TCK로 고정한다([#560](https://github.com/bluetape4k/bluetape4k-graph/issues/560)).
- P3: inclusive damping 경계와 음·양 무한대, BFS/DFS zero-depth 회귀를 현재
  테스트 매트릭스에 포함했다. 추가 backend 경계는 후속 TCK에서 보강한다.

## SPW 게이트

| 항목 | 결과 |
| --- | --- |
| SPW-01 근거·독자·언어 고정 | PASS — graph-core 사용자와 유지보수자를 대상으로 현재 source/test/README와 #540을 대조했다. |
| SPW-02 리뷰 구조 | PASS — 범위, 7-Tier 판정, 위치 근거, 잔여 위험, DoD를 포함했다. |
| SPW-03 한국어 기술 문체 | PASS — 자연스러움·용어를 검토하고 API/명령/숫자는 원문 토큰을 보존했다. |
| SPW-04 사실 추적성 | PASS — 테스트 수와 파일·라인 근거를 fresh 실행 결과에 연결했다. |
| SPW-05 최종 read-back | PASS — 표·코드 토큰·링크·후속 범위를 다시 읽고 diff-check로 확인했다. |

## 최종 판정

**PASS / WATCH** — P0/P1 blocker 없이 #540 수용 기준을 충족한다. P2 후속은
[#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559)와
[#560](https://github.com/bluetape4k/bluetape4k-graph/issues/560)로 추적한다.

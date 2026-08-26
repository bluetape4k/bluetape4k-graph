# #559 weighted path `maxDepth`와 backend depth conformance 7-Tier 코드 리뷰

## DoD 범위

- 이슈: [#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559)
- 선행 PR: [#583](https://github.com/bluetape4k/bluetape4k-graph/pull/583)
- stacked base: `fix/issue-558-suspend-replay-cancellation-stacked`
  exact head `283dbd2fe9c8337699f510a8844d16094bbd06dc`
- 대상: graph-core Dijkstra/A* weighted fallback과 다섯 backend conformance TCK
- 판정: **PASS / WATCH** (P0/P1 blocker 없음; PR hosted receipt는 생성 후 갱신)
- WATCH: weighted path는 공통 JVM fallback을 사용하며 native unweighted query의
  planner semantics를 이 리뷰가 변경하거나 증명하지 않는다.

## SPW evidence ledger

| ID | 확인 내용 | 증거 | 결과 |
| --- | --- | --- | --- |
| SPW-01 | 요구사항·선행 base | live #559, PR #583, #558 exact head, weighted dispatch inventory | PASS |
| SPW-02 | Kotlin/Bluetape 패턴 | immutable state, null-safe lookup, Bluetape assertion fixture | PASS |
| SPW-03 | depth bound | Dijkstra/A* maxDepth 1·2·0 및 cheaper-deep graph unit TCK | PASS |
| SPW-04 | backend parity | Neo4j/Memgraph/AGE/FalkorDB container와 TinkerGraph 공통 fixture | PASS |
| SPW-05 | execution models | sync, virtual-thread, suspend weighted 경로 모두 fixture 호출 | PASS |
| SPW-06 | compatibility | public API, PathOptions validation, positive-weight policy 유지 | PASS |
| SPW-07 | hosted traceability | PR #584 exact head `6309412799416f94bb2367948da9a6a4b4b8744f`, CI `32913808915`, Examples `32913809041` terminal green | PASS |

## 7-Tier 결과

| Tier | 검토 질문 | 결과 |
| --- | --- | --- |
| 1. 요구사항·범위 | weighted `maxDepth`를 실제 edge bound로 고정하고 native unweighted 범위를 건드리지 않는가 | PASS. #559 수용 기준과 정확히 일치한다. |
| 2. API/ABI | public signature와 constructor invariant가 깨지는가 | PASS. 내부 state/reconstructor만 변경한다. |
| 3. Kotlin/Bluetape 패턴 | state immutability, nullability, assertion helper가 일관적인가 | PASS. data class state와 `io.bluetape4k.assertions` matcher를 사용한다. |
| 4. 알고리즘·동시성 | cheaper deep path가 shallow path를 숨기지 않고 deterministic한가 | PASS. `(id, depth)`별 score와 tie-break를 사용한다. |
| 5. 오류·수명주기 | missing vertex/weight와 maxVisited 경계가 기존 정책을 유지하는가 | PASS / WATCH. maxVisited는 depth-aware state expansion 수를 제한한다. |
| 6. 테스트·관측성 | backend·execution model별 경계와 실제 container를 재현하는가 | PASS. 공통 fixture와 다섯 backend 순차 TCK가 통과했다. |
| 7. 문서·유지보수 | EN/KO 계약, matrix, hosted gap이 기록되는가 | PASS / WATCH. PR receipt와 최종 train 상태는 후속 read-back에서 갱신한다. |

## 검증 영수증

- TDD RED: Dijkstra/A*가 `maxDepth=1`에서 두-hop 경로를 반환하고
  `maxDepth=0` source-only 계약을 지키지 않는 실패를 관찰했다.
- TDD GREEN: graph-core Dijkstra/A* targeted 31개 테스트 통과.
- Backend TCK: Neo4j 13개, Memgraph 10개, AGE 10개, FalkorDB 10개 weighted
  sync/suspend 테스트와 TinkerGraph/core fixture 통과.
- 모든 weighted 경로의 virtual-thread adapter도 같은 sync fixture로 검증했다.
- 전체 module test/Detekt, assertion scan, diff-check와 PR exact-head hosted
  receipt를 확인했다.

## P0/P1 판정과 후속 위험

- P0=0, P1=0: 현재 implementation slice를 막는 결함 없음.
- P2: depth-aware state로 인해 `maxVisited`가 vertex 수가 아니라 state expansion
  수를 제한한다. 큰 `maxDepth` 그래프의 비용은 기존 guard로 제한해야 한다.
- P2: heuristic가 admissible하지 않으면 A*의 최적성 보장은 기존과 같이 약화된다.
- P3: 새 weighted backend는 공통 fixture와 sync/suspend/virtual-thread parity를
  추가해야 하며, native query를 도입할 때는 별도 conformance 설계가 필요하다.

## 최종 결론

weighted Dijkstra/A*가 inclusive `PathOptions.maxDepth`를 준수하고, 다섯 backend의
sync/suspend/virtual-thread 결과가 공통 TCK로 정렬된다. **PR readiness: PASS /
Architecture status: WATCH**. PR #584의 CI/Examples hosted receipt도 terminal
green이다. 전체 train merge는 마지막 승인 단계에서만 진행한다.

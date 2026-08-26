# #559 weighted path `maxDepth`와 backend depth conformance TCK 설계

## 문제

`PathOptions.maxDepth`는 public constructor에서 음수 값을 거부하지만, weighted
shortest path의 JVM fallback인 Dijkstra/A*는 이전에 vertex ID만 상태 키로
사용했다. 따라서 더 싼 deep 경로가 더 비싼 shallow 경로를 덮어써서 hop bound
안에 도달할 수 있는 경로를 놓치거나, bound를 넘은 경로를 반환할 수 있었다.
Neo4j, Memgraph, AGE, FalkorDB, TinkerGraph의 weighted 경로가 같은 fallback을
공유하므로 이 경계를 backend별 sync/suspend/virtual-thread conformance TCK로
고정한다.

## 결정

1. Dijkstra와 A*의 탐색 상태를 `(vertexId, depth)`로 모델링한다. predecessor도
   같은 상태를 가리켜 경로 복원 시 vertex ID가 반복되는 깊이 경계를 잃지 않게
   한다.
2. `depth`는 사용한 edge 수이며 `maxDepth`는 inclusive bound다. `maxDepth=0`은
   source와 target이 같은 vertex일 때만 vertex-only path를 허용하고, 서로 다른
   vertex에는 `null`을 반환한다.
3. 현재 public API, `PathOptions` validation, positive weight 정책과 deterministic
   tie-break는 유지한다. `maxVisited`는 depth-aware search state expansion을
   제한하므로 같은 vertex가 서로 다른 depth에서 별도 상태가 될 수 있음을
   문서화한다.
4. weighted backend adapter는 기존 native unweighted query와 분리된 JVM
   fallback을 계속 사용한다. 모든 backend의 sync/suspend/virtual-thread 테스트가
   동일 fixture를 호출해 결과와 경계가 일치하는지 확인한다.

## 범위

- `graph-core` Dijkstra/A* fallback 및 path reconstruction
- graph-core algorithm unit test와 reusable `WeightedPathDepthConformance`
  test fixture
- Neo4j, Memgraph, AGE, FalkorDB, TinkerGraph의 sync/virtual-thread 및 suspend
  weighted path TCK
- graph-core와 root EN/KO README, 7-Tier review, lesson, WIP/CHANGELOG receipt

## 비범위

- unweighted native query의 Cypher/Gremlin path planner
- 새로운 public API 또는 backend-specific weighted query implementation
- negative/zero weight 허용 정책, heuristic admissibility 정책, `maxVisited`의
  새로운 수치 조정

## Backend matrix

| Backend | weighted sync | weighted suspend | virtual-thread | 검증 방식 |
| --- | --- | --- | --- | --- |
| Neo4j | shared JVM fallback | shared JVM fallback | sync adapter | Neo4j Testcontainers |
| Memgraph | shared JVM fallback | shared JVM fallback | sync adapter | Memgraph Testcontainers |
| AGE | shared JVM fallback | shared JVM fallback | sync adapter | AGE Testcontainers |
| FalkorDB | shared JVM fallback | shared JVM fallback | sync adapter | FalkorDB Testcontainers |
| TinkerGraph | shared JVM fallback | shared JVM fallback | sync adapter | in-memory |

## TCK 경계

공통 그래프 `A -1→ B -2→ C`와 `A -5→ C`를 사용한다.

- `maxDepth=1`: 직접 경로 `A→C`, cost `5`
- `maxDepth=2`: 두-hop 경로 `A→B→C`, cost `3`
- `maxDepth=0`: `A→C`는 없음, `A→A`는 vertex-only path
- 더 싼 deep 경로가 있는 경우에도 bound 안의 비싼 shallow 경로를 선택

각 fixture assertion은 `io.bluetape4k.assertions`의
`shouldBeEqualTo`, `shouldBeNear`, `shouldBeNull`, `shouldNotBeNull`을 사용하며,
새 예외 검증이 필요할 때는 `assertFailsWith`만 사용한다.

## 검증 기준

- TDD RED에서 기존 ID-only 상태가 `maxDepth=1` direct path와 `maxDepth=0`
  source-only 계약을 위반함을 확인한다.
- TDD GREEN에서 graph-core algorithm test와 다섯 backend의 sync/suspend/virtual
  TCK를 순차 통과한다.
- 전체 영향 모듈 test/Detekt, 금지 assertion scan, `git diff --check`를 통과한다.
- PR exact base/head, hosted CI/Examples terminal receipt, labels/assignee/
  milestone를 read-back한다. 전체 train merge는 마지막 승인 단계까지 보류한다.

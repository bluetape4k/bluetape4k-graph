# #559 weighted path `maxDepth`와 backend depth conformance lesson

## 상황

weighted shortest path fallback이 vertex ID만으로 최단 비용을 기록하면, 더 싼
deep 경로가 더 비싼 shallow 경로를 덮어쓴다. hop bound가 있는 constrained
shortest path에서는 비용만 최소화하는 상태가 충분하지 않다.

## 결정

Dijkstra와 A*의 상태 및 predecessor를 `(vertexId, depth)`로 확장했다. edge를
하나 사용할 때 depth를 증가시키고 `current.depth >= maxDepth`에서 더 확장하지
않으며, target은 bound 안에서만 반환한다. `maxDepth=0`은 source-only path만
허용한다. 이 방식은 public API를 바꾸지 않고 공통 JVM fallback을 사용하는
Neo4j, Memgraph, AGE, FalkorDB, TinkerGraph의 weighted 결과를 동일하게 만든다.

## 검증

- RED에서 ID-only 구현의 maxDepth 경계 실패와 cheaper-deep/shallow 회귀를 재현했다.
- GREEN에서 graph-core algorithm tests와 다섯 backend의 sync/suspend/virtual-thread
  conformance fixture를 통과했다.
- Bluetape assertions, module test, Detekt, container lifecycle와 문서 matrix를
  확인했다.

## 남은 가드

1. `maxVisited`는 이제 depth-aware state expansion 수를 제한하므로 큰 depth의
   메모리 비용을 계속 bounded하게 유지해야 한다.
2. 이 변경은 weighted JVM fallback 계약이다. native unweighted planner와
   heuristic admissibility는 별도 계약으로 남는다.
3. PR #584 exact-head CI `32913808915`와 Examples `32913809041`가 terminal green임을
   read-back했다. 전체 train은 마지막 일괄 merge 승인 전까지 병합하지 않는다.

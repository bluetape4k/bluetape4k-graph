# #559 weighted path `maxDepth`와 backend depth conformance TCK 실행 계획

## 기준

- live 이슈: [#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559)
- 선행 stacked PR: [#583](https://github.com/bluetape4k/bluetape4k-graph/pull/583)
- base branch: `fix/issue-558-suspend-replay-cancellation-stacked`
- base exact head: `283dbd2fe9c8337699f510a8844d16094bbd06dc`
- target branch: `test/issue-559-weighted-path-depth-stacked`

## 실행 순서

1. Dijkstra/A*와 각 backend의 weighted path dispatch를 inventory하고 native
   unweighted query와 JVM fallback의 경계를 확인한다.
2. `maxDepth=1`, `maxDepth=0`, cheaper-deep/expensive-shallow 그래프를 대상으로
   TDD RED를 추가해 기존 ID-only 상태의 실패를 고정한다.
3. `(vertexId, depth)` state와 depth-aware predecessor reconstruction을 구현해
   algorithm GREEN을 확인한다.
4. graph-core test fixture를 만들고 TinkerGraph부터 sync/virtual-thread,
   suspend 경계를 확인한 뒤 Neo4j→Memgraph→AGE→FalkorDB Testcontainers를
   순차 실행한다.
5. graph-core와 다섯 backend의 전체 영향 module test/Detekt, Bluetape assertion
   scan, diff-check를 실행한다.
6. EN/KO README, spec, 7-Tier review, lesson, WIP/CHANGELOG receipt를 작성하고
   Lore commit으로 push한다.
7. #583 exact head 위에 stacked PR을 만들고 exact base/head·metadata·hosted
   CI/Examples를 read-back한다. 모든 PR은 최종 일괄 merge 승인 전까지 열린
   상태로 유지한다.

## 실패 시 복구

- bound를 넘은 결과가 나오면 expansion guard와 target check를 state depth 기준으로
  분리하되 public API는 바꾸지 않는다.
- cheaper deep state가 shallow state를 숨기면 distance/predecessor map의 key가
  `(id, depth)`인지와 priority queue stale entry 처리를 먼저 확인한다.
- backend 결과가 다르면 adapter의 weighted fallback/native unweighted 분기를
  확인하고 공통 fixture를 backend별 예외로 약화하지 않는다.
- hosted workflow-dispatch image gate가 `BASE_SHA` 입력 부재로 실패하면 코드
  회귀와 분리해 receipt에 기록하고 merge를 보류한다.

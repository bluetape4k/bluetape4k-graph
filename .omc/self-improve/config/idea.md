# Experiment Ideas

## 2-Tier Cache (High Priority — user recommendation)
캐시 구현 시 `bluetape4k-cache-lettuce`의 2-Tier Cache를 사용할 것.
- Local tier: Caffeine (in-process, nanosecond latency)
- Remote tier: Redis/Lettuce (distributed, sub-millisecond latency)
- 단순 Caffeine 캐시 대신 반드시 2-Tier Cache 적용
- 대상 ops: findVertexById, findVerticesByLabel, neighbors, countVertices

## Neo4j ReactiveSession → Dispatchers.IO (carry forward)
- Executor C가 구현 완료, 테스트 통과 (30/30)
- 벤치마크만 실패 — 다음 iteration에서 재측정 필요
- 파일: graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperations.kt

## age_createEdge 최적화 우선 (bottleneck 발견)
- 이번 측정에서 age_createEdge = 8342 μs로 전체 평균을 크게 끌어올림
- 배치 처리 또는 단일 트랜잭션 병합으로 개선 가능

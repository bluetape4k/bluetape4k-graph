# bluetape4k-graph — 향후 작업 목록

> 우선순위는 **임팩트 대비 구현 비용** 기준으로 분류했다.

---

## 1순위 — 핵심 기능 확장

### [ ] 그래프 알고리즘 확장 (`graph-core` + 각 백엔드)

현재 `GraphTraversalRepository`는 `neighbors`, `shortestPath`, `allPaths` 3개뿐.

| 대상 | 설명 | 백엔드 구현 방식 |
|------|------|----------------|
| `pageRank(label, iterations)` | 중요도 순위 — 추천/지식그래프 필수 | Neo4j/Memgraph 내장, AGE/Tinkerpop 직접 구현 |
| `degreeCentrality(vertexId)` | 연결 중심성 — 허브 노드 탐지 | 모든 백엔드 Cypher/Gremlin |
| `connectedComponents(label)` | 연결 컴포넌트 — 고립 클러스터 탐지 | Neo4j/Memgraph GDS, 나머지 직접 구현 |
| `bfs(startId, depth)` | 너비 우선 탐색 | 모든 백엔드 |
| `dfs(startId, depth)` | 깊이 우선 탐색 | 모든 백엔드 |
| `cycles(label)` | 순환 탐지 — 의존성 분석에 필요 | Cypher/Gremlin 직접 구현 |

### [ ] `graph-spring-boot-starter` 신규 모듈

- `application.yml` 프로퍼티 바인딩 (`bluetape4k.graph.neo4j.*` 등)
- `GraphOperations`, `GraphSuspendOperations` 빈 자동 등록
- Spring Boot 3.x + Kotlin AutoConfiguration

### [ ] Streaming API — `Flow<T>` 반환

현재 `findVerticesByLabel`이 `List<T>` 반환 → 대용량 그래프에서 메모리 문제.

```kotlin
// GraphSuspendVertexRepository에 추가
fun streamVerticesByLabel(label: String): Flow<GraphVertex>
fun streamEdgesByLabel(label: String): Flow<GraphEdge>
```

---

## 1순위 (추가) — Virtual Threads 전체 확장

### [ ] Vertex/Edge/Traversal 전체에 Virtual Threads API 적용

**배경**: 그래프 알고리즘 확장(1순위)에서 `GraphVirtualThreadAlgorithmRepository` 브릿지 어댑터를 도입.
동일 패턴을 `GraphVertexRepository`, `GraphEdgeRepository`, `GraphTraversalRepository` 전체로 확대.

**방식**: 각 동기 Repository를 `Executors.newVirtualThreadPerTaskExecutor()`로 감싸는 브릿지 어댑터 + 확장 함수.
신규 인터페이스: `GraphVirtualThreadVertexRepository`, `GraphVirtualThreadEdgeRepository`, `GraphVirtualThreadTraversalRepository`
최종 합성: `GraphVirtualThreadOperations` (Java 코드 진입점).

**선행 조건**: 그래프 알고리즘 확장(1순위) 완료 후 진행.

---

## 2순위 — 생산성 향상

### [ ] `graph-io` 모듈 — 벌크 임포트/익스포트

| 포맷 | 방향 |
|------|------|
| CSV (정점/엣지 분리 파일) | import / export |
| JSON Lines (NDJSON) | import / export |
| GraphML | import / export |

### [ ] 트랜잭션 DSL

```kotlin
ops.transaction {
    val alice = createVertex("Person", mapOf("name" to "Alice"))
    val bob   = createVertex("Person", mapOf("name" to "Bob"))
    createEdge("KNOWS", alice.id, bob.id)
} // 실패 시 롤백
```

### [ ] `graph-benchmark` 모듈 — JMH 벤치마크

- 정점 삽입 1만건, 최단경로 100쌍, 이웃 탐색 1홉/3홉 — 백엔드별 비교
- `docs/graphdb-tradeoffs.md` 실측 수치 보강

---

## 3순위 — 생태계 확장

### [ ] 추가 예시 모듈

| 예시 | 핵심 알고리즘 |
|------|-------------|
| `fraud-detection-examples` | 순환 탐지, 커뮤니티 탐지 |
| `recommendation-examples` | 공통 이웃, 협업 필터링 |
| `knowledge-graph-examples` | 엔티티 링킹, 경로 추론 |

### [ ] 추가 백엔드

| 백엔드 | 이유 |
|--------|------|
| Amazon Neptune | AWS 환경, Bolt 호환으로 구현 비용 낮음 |
| FalkorDB (구 RedisGraph) | Redis 기반 인메모리, Bolt 지원 |

---

## 완료

- [x] 0.1.0 Maven Central 배포 (2026-04-16)
- [x] GitHub Release v0.1.0 작성
- [x] 전체 public API KDoc 커버리지 (199개 예제)
- [x] 추상 테스트 클래스 패턴 (백엔드 전환 용이)
- [x] code-graph-examples, linkedin-graph-examples 통합 모듈

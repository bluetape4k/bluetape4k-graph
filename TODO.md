# bluetape4k-graph — 향후 작업 목록

> 우선순위는 **임팩트 대비 구현 비용** 기준으로 분류했다.

---

## 1순위 — 핵심 기능 확장

### [x] 그래프 알고리즘 확장 (`graph-core` + 각 백엔드) — 2026-04-16 완료

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

설계 문서:

- `docs/superpowers/specs/2026-04-17-spring-boot-starter-design.md`
- `docs/superpowers/plans/2026-04-17-spring-boot-starter-plan.md`

구현 범위:

- `spring-boot3/graph-spring-boot3-starter`
- `spring-boot4/graph-spring-boot4-starter`
- `settings.gradle.kts`에 `includeModules("spring-boot3", false, false)`,
  `includeModules("spring-boot4", false, false)` 추가

자동 구성 원칙:

- `bluetape4k.graph.backend` 단일 프로퍼티로 백엔드 선택 (`tinkergraph`, `neo4j`, `memgraph`, `age`)
- backend별 `enabled` 플래그는 만들지 않는다.
- `GraphOperations`, `GraphSuspendOperations`, `GraphVirtualThreadOperations` 빈은 타입 스코프
  `@ConditionalOnMissingBean`으로 등록한다.
- `@Primary`, alias/wrapper 빈, `ApplicationContext.getBean(...)` 기반 라우팅은 사용하지 않는다.
- `GraphAutoConfiguration`은 공통 `GraphProperties` 바인딩만 담당하고, backend별 AutoConfiguration은
  `AutoConfiguration.imports`에 개별 등록한다.
- backend 클래스는 starter에서 `compileOnly`; 단 `graph-core`는 공개 API 타입 노출을 위해 `api(project(":graph-core"))`로 둔다.

백엔드별 구현:

- TinkerGraph: `TinkerGraphOperations`, `TinkerGraphSuspendOperations`, `ops.asVirtualThread()` 자동 등록
- Neo4j: 기존 `Driver` 빈 재사용, 없으면 `GraphDatabase.driver(...)` 생성
- Memgraph: Neo4j Driver 기반, `MemgraphGraphOperations`, `MemgraphGraphSuspendOperations` 등록
- AGE: Spring Boot 단일 `DataSource` + Exposed `Database.connect(dataSource)` 초기화,
  `AgeGraphOperations.createGraph(graphName)`로 graph 자동 생성
- Actuator HealthIndicator는 nested `HealthConfig` + string-based `@ConditionalOnClass(name = [...])` + FQN 참조로 격리

검증:

- `ApplicationContextRunner` 테스트는
  `AutoConfigurations.of(GraphAutoConfiguration::class.java, <BackendAutoConfiguration>::class.java)`로 root + 대상 backend를 함께 로드
- Spring Web MVC 테스트는 `RANDOM_PORT + TestRestTemplate`로 Virtual Thread 실행 여부 검증
- WebFlux 테스트는 `WebTestClient`와 suspend controller로 `GraphSuspendOperations` 검증
- Neo4j/Memgraph/AGE는 `graph-servers` Testcontainers 싱글턴 재사용
- `dependencyInsight`로 Boot 3 starter가 Spring Boot 3.5.x를 참조하는지 확인
- `wiki/testlogs/2026-04.md`, README/README.ko.md, `docs/superpowers/index/2026-04.md` 갱신

### [ ] Streaming API — `Flow<T>` 반환

현재 `findVerticesByLabel`이 `List<T>` 반환 → 대용량 그래프에서 메모리 문제.

```kotlin
// GraphSuspendVertexRepository에 추가
fun streamVerticesByLabel(label: String): Flow<GraphVertex>
fun streamEdgesByLabel(label: String): Flow<GraphEdge>
```

---

## 1순위 (추가) — Virtual Threads 전체 확장

### [x] Vertex/Edge/Traversal 전체에 Virtual Threads API 적용 — 2026-04-17 완료

**배경**: 그래프 알고리즘 확장(1순위)에서 `GraphVirtualThreadAlgorithmRepository` 브릿지 어댑터를 도입.
동일 패턴을 `GraphVertexRepository`, `GraphEdgeRepository`, `GraphTraversalRepository` 전체로 확대.

**방식**: 각 동기 Repository를 `Executors.newVirtualThreadPerTaskExecutor()`로 감싸는 브릿지 어댑터 + 확장 함수.
신규 인터페이스: `GraphVirtualThreadVertexRepository`, `GraphVirtualThreadEdgeRepository`, `GraphVirtualThreadTraversalRepository`
최종 합성: `GraphVirtualThreadOperations` (Java 코드 진입점).

**선행 조건**: 그래프 알고리즘 확장(1순위) 완료 후 진행.

---

## 2순위 — 생산성 향상

### [ ] 문서 / 예제 API 정합성 정리

현재 코드와 일부 README 예제가 어긋난다. 신규 starter 문서화 전에 먼저 정리한다.

- `AgeGraphOperations(database, graphName = "...")` 예제를 현재 생성자 `AgeGraphOperations(graphName)` +
  `Database.connect(dataSource)` 선행 호출 패턴으로 수정
- `AgeGraphSuspendOperations` 예제도 동일하게 `AgeGraphSuspendOperations(graphName)` 기준으로 정리
- `asVirtualThread` import 예제를 실제 패키지 `io.bluetape4k.graph.vt.asVirtualThread` 기준으로 정리
- 루트 `README.md`, `README.ko.md`, `graph-age/README*.md`, `graph-servers/README*.md`,
  `graph-core/README*.md`에서 오래된 코드 조각 검색 후 수정
- README 코드 조각과 실제 테스트 코드가 컴파일 가능한 형태인지 샘플 테스트 또는 문서 스니펫 점검으로 확인

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

### [ ] 동기 / Virtual Threads / Coroutines API 벤치마크

알고리즘 확장으로 추가된 3가지 실행 모델의 처리량·지연 시간 비교 (JMH, TinkerGraph 인메모리 기준).

| 측정 항목 | 설명 |
|-----------|------|
| `pageRank` throughput | 동기 vs `asVirtualThread()` vs `Flow` 수집 |
| `bfs` latency (depth=5) | 3가지 API 왕복 시간 |
| 동시 요청 처리량 | VT 100-way 병렬 vs 코루틴 `async` 100-way |
| 스레드/메모리 사용량 | Virtual Thread 생성 비용 측정 |

- 측정 대상: `TinkerGraphOperations` (Docker 불필요)
- 결과 기록: `docs/graphdb-tradeoffs.md` API 모델 비교 섹션 추가

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

- [x] 그래프 알고리즘 확장 — 6 알고리즘 × 4 백엔드 + VT bridge (2026-04-16)
- [x] Virtual Threads 전체 확장 — Vertex/Edge/Traversal/Operations 어댑터 + 테스트 (2026-04-17)
- [x] 0.1.0 Maven Central 배포 (2026-04-16)
- [x] GitHub Release v0.1.0 작성
- [x] 전체 public API KDoc 커버리지 (199개 예제)
- [x] 추상 테스트 클래스 패턴 (백엔드 전환 용이)
- [x] code-graph-examples, linkedin-graph-examples 통합 모듈

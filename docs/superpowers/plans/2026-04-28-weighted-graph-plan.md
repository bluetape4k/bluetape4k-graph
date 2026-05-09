# 가중치 그래프 지원 구현 계획

- **Issue**: #31 가중치 그래프 지원 — weighted edge + Dijkstra / A\* 최단경로
- **Spec**: [`docs/superpowers/specs/2026-04-28-weighted-graph-design.md`](../specs/2026-04-28-weighted-graph-design.md)
- **Status**: Plan (Ready)
- **Author**: bluetape4k-graph maintainers
- **Date**: 2026-04-28
- **관련 모듈**: `graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`, `graph-falkordb`, `examples/*`, `benchmark/graph-benchmark`

---

## 개요

본 계획은 Spec(2026-04-28)에서 채택된 **안 B (JVM 폴백 단일 구현)** 와 **A\* 별도 메서드** 결정을 구현 가능한 단위로 분해한다.

설계 핵심 5가지:

1. **JVM fallback 단일 구현** — 모든 백엔드(neo4j/memgraph/age/tinkerpop/falkordb)가 `graph-core`의 `DijkstraRunner`/`AStarRunner`에 위임. 결과 결정성과 코드 중복 제거를 동시에 달성.
2. **`PathOptions` 비파괴 확장** — `weightProperty`, `missingWeightPolicy`, `direction`, `maxVisited` 모두 default 값. 기존 호출 사이트는 컴파일 변경 없음.
3. **`GraphPath.totalWeight` 저장 필드** — 가중치 모드 결과를 메타데이터로 보존. unweighted 경로는 length 와 동일한 Double.
4. **신규 레포지토리 메서드** — `findVertexById(id)` (label-only), `findEdgesByStartId(id, label?)`, `findEdgesByEndId(id, label?)` (sync + suspend). `ShortestPathFallback`이 이 API 를 사용해 백엔드 무관하게 adjacency 를 lazy 로 가져온다.
5. **`aStarPath` 직접 override** — interface default method 가 `GraphOperations` 합성 타입을 요구하기 때문에, `GraphTraversalRepository`에서는 시그니처만 추가하고 default 본체는 제공하지 않는다. 각 백엔드 `*GraphOperations` 가 직접 `override` 하여 `ShortestPathFallback.aStar(this, ...)` 를 호출한다.

작업 분해 전략: graph-core 기반(모델/옵션/예외 → 레포 인터페이스 → 알고리즘 내부) → 각 백엔드(인터페이스 구현 → shortestPath 분기 → aStarPath override → suspend 변형 → caching 위임) → 테스트(unit → abstract base → backend별) → 벤치마크 → 문서. 백엔드 5개의 작업은 동일 패턴이므로 그룹 task 로 묶고, 백엔드별로 sync/suspend/caching 3변형을 병렬 작업한다.

---

## Task List

### T1 — `MissingWeightPolicy` + `MissingWeightException` 신규 작성

- **complexity**: low
- **scope**: 1 file (graph-core, ~50 LOC)
- **설명**: `graph-core/.../model/MissingWeightPolicy.kt` 파일을 생성한다. `sealed class MissingWeightPolicy : Serializable` 와 `data object Fail`, `data object Skip`, `data class UseDefault(value: Double)` 구현. `UseDefault.init { require(value > 0.0 && value.isFinite()) }`. 같은 파일에 `class MissingWeightException(edgeId, key) : IllegalStateException(...)` 정의. 모든 sealed 분기와 companion 에 `serialVersionUID = 1L`.
- **완료 기준**: 빌드 통과. `MissingWeightPolicy.UseDefault(0.0)` / `UseDefault(Double.NaN)` / `UseDefault(Double.POSITIVE_INFINITY)` 가 IllegalArgumentException 으로 거부되는 단위 테스트 작성.

### T2 — `GraphPath` 에 `totalWeight: Double` 필드 추가

- **complexity**: medium
- **scope**: 1 file (graph-core) + 회귀 검증
- **설명**: `data class GraphPath(steps, totalWeight: Double = ...)` 두 번째 파라미터 추가. 기본값은 `steps.filterIsInstance<PathStep.EdgeStep>().size.toDouble()`. `EMPTY = GraphPath(emptyList(), 0.0)` 와 `of(vararg vertices)` 도 갱신(`totalWeight = 0.0`). `serialVersionUID` 유지. 아래 5개 생산 코드 call site 를 명시적으로 확인 후 업데이트:
  - `AgeGraphOperations.kt:397`
  - `AgeTypeParser.kt:92`
  - `FalkorDBGraphOperations.kt:586`
  - `FalkorDBGraphSuspendOperations.kt:640`
  - `GraphPath.kt:87, 102` (companion `EMPTY`/`of`)
  코드베이스 전체에서 `val (` 또는 `componentN` 으로 destructuring 사용처도 grep 확인.
- **완료 기준**: destructuring 사용처 0 확인. 5개 call site 모두 업데이트 완료. `GraphPath.equals`/`hashCode` 변경으로 기존 단언 실패 여부 확인. 직렬화 라운드트립 테스트 통과.

### T3 — `PathOptions` 4개 신규 필드 추가

- **complexity**: medium
- **scope**: 1 file (graph-core) + Direction enum 위치 확인
- **설명**: `data class PathOptions(...)` 에 `weightProperty: String? = null`, `missingWeightPolicy: MissingWeightPolicy = MissingWeightPolicy.Fail`, `direction: Direction = Direction.BOTH`, `maxVisited: Int = 100_000` 추가. `init` 블록에 `require(maxDepth >= 0)`, `require(maxVisited > 0)` 추가. companion `Default = PathOptions()`. `Direction` 이 graph-core 에 없으면 `model/Direction.kt` 신규 작성(`enum class Direction { OUTGOING, INCOMING, BOTH }`).
- **완료 기준**: 기존 호출(`PathOptions(edgeLabel="...")`) 모두 컴파일 통과. destructuring 사용처 0 확인. 단위 테스트로 음수 maxDepth/0 maxVisited 거부 검증.

### T4 — `GraphVertexRepository` / `GraphSuspendVertexRepository` 에 `findVertexById(id)` overload 추가

- **complexity**: medium
- **scope**: 2 interface files + impact 분석
- **설명**: 기존 `findVertexById(label, id)` 옆에 label 없이 ID 만으로 조회하는 `fun findVertexById(id: GraphElementId): GraphVertex?` 추가. suspend 버전 동일. interface default 구현이 가능한지 검토 — 가능하면 default 로 모든 라벨 검색, 불가능하면 abstract 로 두고 백엔드별 구현. (백엔드들이 cypher/gremlin 으로 자연스럽게 ID 단독 매칭이 가능하므로 abstract 권장.)
- **완료 기준**: `graph-core` 빌드 통과. 모든 백엔드는 후속 task 에서 구현 (T9 ~ T13).

### T5 — `GraphEdgeRepository` / `GraphSuspendEdgeRepository` 에 `findEdgesByStartId` / `findEdgesByEndId` 추가

- **complexity**: medium
- **scope**: 2 interface files
- **설명**: 두 메서드 모두 `(id: GraphElementId, edgeLabel: String? = null): List<GraphEdge>` 시그니처. suspend 버전은 `Flow<GraphEdge>` 또는 `List<GraphEdge>` — 기존 EdgeRepository 패턴 따름(현재 패턴 확인 후 일관성 유지). KDoc 에 "결과 순서는 startId/endId 의 lexicographic 오름차순 정렬되어야 한다 — Dijkstra 결정성 보장 위해" 명시.
- **완료 기준**: 인터페이스 빌드 통과. 결정성 요구사항이 KDoc 으로 명시됨.

### T6 — `GraphTraversalRepository` 변형 3종에 `aStarPath` 시그니처 추가

- **complexity**: medium
- **scope**: 3 interface files (sync/suspend/VT)
- **설명**:
  - `GraphTraversalRepository.aStarPath(fromId, toId, heuristic: (GraphVertex) -> Double, options: PathOptions): GraphPath?` — default 구현 없음, abstract.
  - `GraphSuspendTraversalRepository.aStarPath(...)` 동일 (suspend) — heuristic 은 동기 함수만 허용 (KDoc 에 명시).
  - `GraphVirtualThreadTraversalRepository.aStarPathAsync(...): CompletableFuture<GraphPath?>` — 기존 `*Async` 명명 패턴 준수.
  - `VirtualThreadTraversalAdapter` 구현부에서 `aStarPathAsync` 를 `supplyAsync(virtualExecutor) { underlying.aStarPath(...) }` 로 위임.
  - **주의**: graph-core 내 `GraphTraversalRepository` 구현체(test double/fake 등)가 있으면 `aStarPath` stub 을 추가해 컴파일 유지. `grep -r "GraphTraversalRepository" graph-core/src/test` 로 확인.
- **완료 기준**: 시그니처만 추가. 백엔드 구현 미완 상태에서는 컴파일 에러 발생 — 후속 backend tasks 에서 해소. graph-core test fake 가 있으면 stub 추가.

### T7 — `WeightExtractor` 작성

- **complexity**: high
- **scope**: 1 file (graph-core, ~120 LOC) + 단위 테스트
- **설명**: `internal object WeightExtractor` 의 `extract(edge, key, policy): Double?` 구현. 검증 순서 엄수: 결측→정책 적용 / Number 타입 / BigDecimal/BigInteger overflow / `toDouble()` / NaN 거부 / `isFinite()` 거부 / `>= 0.0` 검증. `Fail` 정책 결측 시 `MissingWeightException`, 그 외 위반 시 `IllegalArgumentException` (메시지에 edge id, key, raw value 포함). `Skip` 결측 시 null 반환.
- **완료 기준**: Int/Long/Double/Float/BigDecimal/BigInteger 변환 단위 테스트, NaN/+Infinity/-Infinity/음수 거부, BigDecimal `Double.MAX_VALUE` 초과 거부, BigInteger `Long.MAX_VALUE` 초과 거부, Skip/Fail/UseDefault 분기 모두 통과. 커버리지 95% 이상.

### T8 — `PathReconstructor` 헬퍼 작성

- **complexity**: low
- **scope**: 1 file (graph-core, ~40 LOC)
- **설명**: 두 러너가 공유할 경로 복원 로직. `reconstruct(parent: Map<GraphElementId, Pair<GraphEdge, GraphElementId>>, fromId, toId, vertices: Map<GraphElementId, GraphVertex>): List<PathStep>` — to 에서 from 까지 역추적 후 reverse, `[V, E, V, E, V]` 형태로 반환. fromId 자체가 toId 인 경우 `[VertexStep(from)]` 단일 vertex 경로.
- **완료 기준**: 단위 테스트로 1-hop / multi-hop / self path 검증.

### T9 — `DijkstraRunner` 작성

- **complexity**: high
- **scope**: 1 file (graph-core, ~150 LOC) + 단위 테스트
- **설명**: `internal object DijkstraRunner` + `KLogging companion`. `data class Result(path: GraphPath?, visitedCount: Int, truncated: Boolean = false)`. `run(fromId, toId, maxDepth, maxVisited, fetchIncident, weightOf, startVertex): Result`.
  - `java.util.PriorityQueue` 사용, `Comparator.comparingDouble { it.cost }.thenComparing { it.vertexId }` (이중 보장).
  - `settled: HashSet<GraphElementId>`, `parent: HashMap<...>`, `vertexCache: HashMap<GraphElementId, GraphVertex>` (PathReconstructor 에 전달).
  - `fetchIncident(v).sortedBy { it.second.id.value }` 후 PQ push (cross-backend 결정성).
  - `weightOf(edge)` 가 null (Skip 결측) → 해당 간선 건너뜀.
  - `settled.size > maxVisited` → WARN 로그 후 `Result(null, settled.size, truncated=true)`.
  - `depth >= maxDepth` → WARN 로그 후 더 이상 확장하지 않음 (continue).
  - to 도달 시 break, PathReconstructor 호출, `totalWeight = dist[to]`.
  - DEBUG 로그: 시작/완료 정보 (from, to, visited, totalWeight).
- **완료 기준**: 단위 테스트 — 직선 / 분기 / 동등비용 결정성 / self-loop 무시 / 도달불가 null / maxDepth 초과 / maxVisited 초과(truncated=true) / 결측+Fail/Skip/UseDefault. 커버리지 95%+.

### T10 — `AStarRunner` 작성

- **complexity**: high
- **scope**: 1 file (graph-core, ~150 LOC) + 단위 테스트
- **설명**: `internal object AStarRunner` + `KLogging`. Dijkstra 와 동일한 시그니처에 `heuristic: (GraphVertex) -> Double` 파라미터 추가. PQ 키는 `f = g + h(v)`, `g[v]` 별도 관리, `closed` 집합 관리. heuristic 은 이미 `safeHeuristic` (NaN/Infinity/음수 검증 래핑) 으로 들어온다고 가정. PathReconstructor 공유 사용.
- **완료 기준**: 단위 테스트 — admissible heuristic(h=0)이 Dijkstra 와 동일 결과 / admissible(직선거리)이 visited 감소 / non-admissible 도 종료 보장 / maxDepth & maxVisited / 잘못된 heuristic 입력은 ShortestPathFallback 단계에서 거부됨 (러너 자체는 안전한 입력 가정).

### T11 — `ShortestPathFallback` 작성

- **complexity**: high
- **scope**: 1 file (graph-core, ~150 LOC)
- **설명**: `internal object ShortestPathFallback` + `KLogging`. 두 public 함수.
  - `dijkstra(ops: GraphOperations, fromId, toId, options): GraphPath?` — `requireNotNull(options.weightProperty)`, `ops.findVertexById(fromId/toId)` 검증, `buildFetchIncident(ops, options)` (private), `weightOf = { WeightExtractor.extract(it, weightKey, policy) }`, `DijkstraRunner.run(...)` 호출.
  - `aStar(ops, fromId, toId, heuristic, options): GraphPath?` — 동일 + `safeHeuristic` 래핑 (`require(h.isFinite() && h >= 0.0)`) 후 `AStarRunner.run(...)` 호출.
  - `private fun buildFetchIncident(ops, options): (GraphElementId) -> Sequence<Pair<GraphEdge, GraphVertex>>` — `direction` 분기 (OUTGOING/INCOMING/BOTH) 로 `findEdgesByStartId` / `findEdgesByEndId` 호출, vertex 미조회 시 해당 쌍 제외, BOTH 일 때 dedup 은 DijkstraRunner의 settled 가 흡수하므로 단순 concat.
- **완료 기준**: graph-core 빌드 통과. 통합 테스트는 백엔드별 task 에서 검증.

### T11.5 — `ShortestPathFallbackTest` 단위 테스트 (fake GraphOperations)

- **complexity**: medium
- **scope**: 1 test file (graph-core)
- **설명**: fake `GraphOperations` (in-memory adjacency map) 으로 `ShortestPathFallback` 직접 테스트. direction 분기 (OUTGOING/INCOMING/BOTH), `from` 미존재 → null, `to` 미존재 → null, `weightProperty = null` → IllegalArgumentException, 정상 가중치 경로 등 시나리오. PathReconstructor 통합 검증도 겸함.
- **완료 기준**: OUTGOING/INCOMING/BOTH 각 방향 테스트 통과. early-exit null 케이스 통과.

### T12 — Neo4j 백엔드 구현 (sync/suspend/caching)

- **complexity**: high
- **scope**: 3 files (`Neo4jGraphOperations.kt`, `Neo4jGraphSuspendOperations.kt`, `CachingNeo4jGraphOperations.kt`)
- **설명**:
  - sync `Neo4jGraphOperations`: `findVertexById(id)` (label 없는 cypher: `MATCH (n) WHERE elementId(n) = $id RETURN n`), `findEdgesByStartId(id, edgeLabel?)`, `findEdgesByEndId(id, edgeLabel?)` 구현 (정렬: `ORDER BY elementId(neighbor)`). `shortestPath` 에 `when (options.weightProperty) { null -> 기존 / else -> ShortestPathFallback.dijkstra(this, ...) }` 분기. `aStarPath` override → `ShortestPathFallback.aStar(this, ...)`.
  - suspend 버전: `withContext(Dispatchers.IO) { underlyingSync.* }` 로 위임 또는 native Driver Async 사용 (현재 패턴 확인 후 일관성).
  - caching 버전: 신규 메서드도 upstream 위임만, 캐시는 후속 이슈로 유보.
- **완료 기준**: graph-neo4j 모듈 빌드 통과. 통합 테스트 (`Neo4jWeightedShortestPathTest`) 통과.

### T13 — Memgraph 백엔드 구현 (sync/suspend/caching)

- **complexity**: high
- **scope**: 3 files
- **설명**: T12 와 동일 패턴. Memgraph 는 Neo4j 드라이버 호환이지만 `elementId()` 미지원 → `id(n)` 사용. cypher 쿼리는 Memgraph 방언 점검 필수. caching 변형 동일 위임.
- **완료 기준**: graph-memgraph 빌드 + 통합 테스트 통과.

### T14 — AGE 백엔드 구현 (sync/suspend/caching)

- **complexity**: high
- **scope**: 3 files (`AgeGraphOperations.kt` 등)
- **설명**: T12 와 동일 패턴. AGE 는 Cypher-over-SQL 이므로 `findVertexById` / `findEdges*` 의 SQL 래퍼 작성 (기존 BFS 구현체 참조). properties JSON 추출 시 weight 가 jsonb number 로 들어오므로 `WeightExtractor` 가 받아들일 수 있는 `Number` 타입으로 변환되도록 매퍼 점검.
- **완료 기준**: graph-age 빌드 + 통합 테스트 통과.

### T15 — TinkerPop 백엔드 구현 (sync/suspend)

- **complexity**: high
- **scope**: 2 files (TinkerPop 은 Caching 변형 없음)
- **설명**: T12 와 동일 패턴. Gremlin `g.V(id)` / `g.V(id).outE(label).order().by(inV().id())` 등으로 결정적 정렬 보장. 인메모리 TinkerGraph 라서 라이프사이클 단순. 체크리스트:
  - `findVertexById(id: GraphElementId)` 구현
  - `findEdgesByStartId(id, edgeLabel?)` 구현 (정렬 보장)
  - `findEdgesByEndId(id, edgeLabel?)` 구현 (정렬 보장)
  - `shortestPath` 분기 (`weightProperty != null → ShortestPathFallback.dijkstra`)
  - `aStarPath` override → `ShortestPathFallback.aStar`
  - suspend 미러 (5개 메서드 전부)
- **완료 기준**: graph-tinkerpop 빌드 + 통합 테스트 통과.

### T16 — FalkorDB 백엔드 구현 (sync/suspend)

- **complexity**: high
- **scope**: 2 files (FalkorDB 는 Caching 변형 없음)
- **설명**: T12 와 동일 패턴. FalkorDB 는 openCypher 부분집합. `MATCH (n) WHERE id(n) = $id` 로 ID 단독 매칭. jfalkordb 결과 매핑에서 properties Number 타입 보존 점검. 체크리스트:
  - `findVertexById(id: GraphElementId)` 구현
  - `findEdgesByStartId(id, edgeLabel?)` 구현 (정렬 보장)
  - `findEdgesByEndId(id, edgeLabel?)` 구현 (정렬 보장)
  - `shortestPath` 분기
  - `aStarPath` override
  - suspend 미러
- **완료 기준**: graph-falkordb 빌드 + 통합 테스트 통과.

### T17 — graph-core 단위 테스트 보강 (DijkstraRunner / AStarRunner / WeightExtractor / GraphPath 직렬화 / PathReconstructor)

- **complexity**: medium
- **scope**: 5 test files
- **설명**: T1, T7, T8, T9, T10 에서 작성한 테스트를 한 곳에 모아 누락된 시나리오 보강 — `DijkstraRunnerTest`, `AStarRunnerTest`, `WeightExtractorTest`, `GraphPathSerializationTest`, `PathReconstructorTest`. `PathReconstructorTest` 는 T8 완료 기준에 나온 1-hop / multi-hop / self path 시나리오. 각 테스트는 AAA 패턴 + 결정성 (vertex id lexicographic tie-break) 검증 포함. `bluetape4k-assertions` + JUnit 5 사용.
- **완료 기준**: graph-core test 커버리지 신규 코드 95%+. 모든 시나리오(Spec 6.1) 커버.

### T18 — `Abstract*WeightedShortestPathTest` 추상 베이스 + 백엔드별 구체 테스트 5종 (sync)

- **complexity**: high
- **scope**: 1 abstract + 5 concrete test files (`graph-{neo4j,memgraph,age,tinkerpop,falkordb}` 의 src/test)
- **설명**: 추상 클래스에 Spec 6.2 그래프 (`A→B→D, A→C→D, A→E dead-end`) 적재 로직 + 시나리오. `shortestPath(weighted) == A→B→D` (totalWeight=3), `aStarPath(admissible) == 동일`, 결측 정책 3종, maxDepth/maxVisited 한도, cross-backend equality (totalWeight + vertex id 순서). 구체 클래스는 백엔드 driver/ops 셋업만 제공. `@TestInstance(PER_CLASS)` + `@BeforeAll` 컨테이너 라이프사이클.
- **완료 기준**: 5개 백엔드 모두 동일 시나리오에서 동일 결과 반환 (cross-backend assertion 헬퍼로 검증).

### T19 — Suspend 변형 통합 테스트 (`Abstract*WeightedShortestPathSuspendTest` + 5 backend)

- **complexity**: medium
- **scope**: 1 abstract + 5 concrete test files
- **설명**: T18 의 suspend 거울. `kotlinx-coroutines-test` `runTest` 사용, `Dispatchers.IO` 컨텍스트에서 동작 검증. heuristic 은 동기 람다.
- **완료 기준**: suspend 변형이 sync 와 동일 결과 반환.

### T20 — 회귀 테스트 — 기존 unweighted `shortestPath` 동작 보존

- **complexity**: low
- **scope**: 추가 테스트 케이스 1~2 개 (각 백엔드 기존 테스트 클래스에 추가 또는 abstract 추가)
- **설명**: `PathOptions()` (default, weightProperty=null) 호출이 기존 hop 수 기준 결과를 그대로 반환하는지 검증. `GraphPath.totalWeight == length.toDouble()` 보장. `GraphPath` 직렬화 라운드트립 (totalWeight 포함). `GraphPath.equals`/`hashCode` 회귀 — 동일 steps 의 두 path 에서 totalWeight 차이가 있으면 equals == false 임을 명시적으로 단언(기존 테스트 조정 필요 여부 확인).
- **완료 기준**: 기존 회귀 테스트 + 신규 회귀 단언 모두 통과. `./gradlew test` 그린.

### T21 — `WeightedShortestPathBench` JMH 벤치마크 + 결과 기록

- **complexity**: medium
- **scope**: 1 bench file (benchmark/graph-benchmark) + `docs/benchmark/weighted-shortest-path.md`
- **설명**: V=1000, E=avg 5 무작위 그래프 (가중치는 1.0~10.0 uniform). Dijkstra (weighted) vs BFS (unweighted) p50/p95 latency. 축: Sync / VirtualThread / Suspend (3축). JMH `@Setup(Trial)` 에서 그래프 한 번 적재, `@Benchmark` 메서드는 무작위 from/to 페어 호출. 결과를 `docs/benchmark/` 에 마크다운 표로 기록. 백엔드는 TinkerGraph(인메모리) + Neo4j(컨테이너) 2종으로 한정 (시간 제약).
- **완료 기준**: 벤치마크 실행 후 결과 파일 commit. memory 시드 기준 결과 재현 가능.

### T22 — README / CHANGELOG / KDoc 업데이트

- **complexity**: low
- **scope**: README.md / README.ko.md / CHANGELOG.md + 모든 신규 public 심볼 KDoc 보강
- **설명**:
  - README 에 "가중치 최단 경로" 섹션 추가 (Dijkstra + A\* 사용 예시).
  - CHANGELOG 에 "0.x.y — Weighted shortest path (Dijkstra/A\*) 추가" 항목.
  - `aStarPath` / `PathOptions` 신규 필드 / `MissingWeightPolicy` / `findVertexById(id)` / `findEdgesByStartId/EndId` 모두 KDoc 보강 (`@param`, `@throws`, `@since`).
  - 선택적: `docs/superpowers/wiki/weighted-graph-usage.md` 사용 가이드 (별도 PR 가능).
- **완료 기준**: 빌드 시 dokka 경고 0. README 코드 블록이 실제로 컴파일/실행 가능한 형태.

### T22.5 — bluetape4k-patterns 체크리스트 검증

- **complexity**: low
- **scope**: 모든 신규/수정 파일 검토
- **설명**: `bluetape4k-patterns` skill 실행. 신규 파일 전체에 대해:
  - 모든 `internal object` / `class` 에 `companion object : KLogging()` 또는 `KLoggingChannel()` 존재 확인
  - public API 에 `@param` / `@throws` / `@since` KDoc 완비
  - magic number 미존재 (named constant 사용)
  - AtomicFU 적용 필요 부분 없는지 확인
  - immutability 위반 없음
- **완료 기준**: 패턴 위반 0. detekt/ktlint 경고 0.

### T23 — Definition of Done 최종 게이트 (전체 빌드/테스트/벤치마크 검증)

- **complexity**: medium
- **scope**: 작업이 아닌 게이트 — 전체 검증
- **설명**:
  - `./gradlew clean build` 통과 (모든 모듈).
  - `./gradlew test` 통과.
  - 신규 코드 커버리지 ≥ 80% (graph-core 러너/추출기 95%+).
  - ktlint / detekt 통과.
  - 벤치마크 결과 기록 완료.
  - 기존 unweighted 테스트 회귀 0.
  - PR 생성 (commit-push-pr 플로우, conventional commits, 이슈 #31 close 라벨).
- **완료 기준**: PR open + 모든 CI 통과 + code-reviewer 에이전트 최종 검토 완료.

---

## 작업 순서 / 의존성

```
T1, T2, T3 (model)            ─┐
T4, T5, T6 (interface)         ├─→ T7, T8 (utils) ─→ T9, T10 (runners) ─→ T11 (fallback) ─→ T11.5 (fallback unit test)
                                                                              │
                                                                              ▼
                                       T12, T13, T14, T15, T16 (backends, parallel)
                                                                              │
                                                                              ▼
                 T17 (core unit, +PathReconstructor) ─→ T18, T19 (integration) ─→ T20 (regression)
                                                                              │
                                                                              ▼
                                         T21 (bench) ─→ T22 (docs) ─→ T22.5 (patterns) ─→ T23 (DoD gate)
```

- T12~T16 은 백엔드별 독립이므로 병렬 가능.
- T17 은 T9/T10/T7/T2 직후 즉시 진행.
- T20 은 T18/T19 와 병렬 가능 (기존 테스트 수정 없는 한 회귀 단언만 추가).
- T21 은 T16 까지 완료 후 진행 (백엔드 안정 후 측정 의미 있음).

## 리스크 트래킹 (Spec §2.2 매핑)

| 리스크 | Task |
|--------|------|
| R1 음수 weight | T7 (WeightExtractor), T17 |
| R2 NaN/Infinity | T7, T17 |
| R3 결측 weight | T1, T7, T17, T18 |
| R4 거대 그래프 OOM | T3 (maxVisited), T9 (truncated), T17 |
| R5 부동소수점 비결정성 | T9 (PQ 이중 키), T11 (vertex id 정렬), T18 (cross-backend assertion) |
| R6 self loop | T9 (settled 흡수) + T17 단위 테스트 |
| R7 non-admissible heuristic | T10 (종료 보장), T22 (KDoc 경고) |
| R8 동시성 | 기존 GraphSession 패턴 재사용 (변경 없음) — 회귀 테스트 T20 으로 가시성 |

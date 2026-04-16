# Graph Algorithm Extension Design Spec

- **Date**: 2026-04-16
- **Status**: Revised (critic 리뷰 반영 완료)
- **Module**: `graph/graph-core` (interface), `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop` (impl)
- **Author**: bluetape4k-graph

---

## 1. 배경 및 목표

현재 `GraphTraversalRepository` / `GraphSuspendTraversalRepository`는 `neighbors`,
`shortestPath`, `allPaths` 3개의 기본 순회 API만 제공한다. 본 스펙은 다음 6개의
그래프 알고리즘을 백엔드 독립 인터페이스로 확장하는 것을 목표로 한다.

| # | 알고리즘 | 목적 |
|---|----------|------|
| 1 | `pageRank(label, iterations)` | 정점 중요도 순위 |
| 2 | `degreeCentrality(vertexId)` | 연결 중심성 (in/out/total) |
| 3 | `connectedComponents(label)` | 연결 컴포넌트 탐지 |
| 4 | `bfs(startId, maxDepth)` | 너비 우선 탐색 |
| 5 | `dfs(startId, maxDepth)` | 깊이 우선 탐색 |
| 6 | `detectCycles(label)` | 순환 탐지 |

**원칙**

- 옵션 계층을 두 개로 분리:
  - **`GraphTraversalOptions`** (기존): `maxDepth` 필수 — BFS/DFS/Cycle 전용.
  - **`GraphAlgorithmOptions`** (신규): `maxDepth` 없음 — PageRank/Degree/CC 전용.
- **3종 비동기 API** 동시 추가:
  - 동기: `GraphAlgorithmRepository` (blocking)
  - 코루틴: `GraphSuspendAlgorithmRepository` (`Flow<T>` / `suspend fun`)
  - Virtual Threads: `GraphVirtualThreadAlgorithmRepository` (`CompletableFuture<T>`, Java interop)
- 모든 public API는 한국어 KDoc 필수. `KLogging` companion object는 **구체 구현 클래스**에만 추가 (인터페이스 제외).
- Result 타입은 `data class : Serializable` + `companion object { private const val serialVersionUID: Long = 1L }`.
- 백엔드별 미구현은 `UnsupportedOperationException` 가능 (Tier-2 알고리즘).

---

## 2. 현재 인터페이스 패턴 분석

### 2.1 옵션 sealed 계층

**기존** — `GraphTraversalOptions` (`maxDepth` 필수, traversal 전용):
```kotlin
sealed class GraphTraversalOptions: Serializable {
    abstract val maxDepth: Int
}
data class NeighborOptions(edgeLabel, direction, maxDepth=1)  // traversal
data class PathOptions   (edgeLabel,            maxDepth=10) // traversal
```

**신규** — `GraphAlgorithmOptions` (analytics 전용, `maxDepth` 없음):
```kotlin
/** Analytics 알고리즘 옵션 공통 sealed 클래스. maxDepth 개념이 없는 알고리즘에 사용. */
sealed class GraphAlgorithmOptions : Serializable
// 하위: PageRankOptions, DegreeOptions, ComponentOptions
```

BFS/DFS/Cycle은 탐색 깊이가 의미 있으므로 `GraphTraversalOptions` 하위에 남긴다.
모든 옵션 클래스는 `companion object { val Default = ... }` 패턴을 따른다.

### 2.2 동기 / 코루틴 짝 패턴

- 동기: `List<T>` / nullable / scalar 반환.
- 코루틴: 컬렉션은 `Flow<T>`, 단일/스칼라는 `suspend fun ... : T?` 반환.

### 2.3 ID 타입

`GraphElementId` (inline value class wrapping String). AGE Long ID, Neo4j elementId(),
TinkerGraph object ID 가 모두 String 으로 통합됨.

---

## 3. API 설계

### 3.1 신규 옵션 클래스

#### `GraphAlgorithmOptions` 하위 — analytics (maxDepth 없음)

신규 파일: `graph-core/.../model/GraphAlgorithmOptions.kt`

```kotlin
/** Analytics 알고리즘 옵션 공통 sealed 클래스. */
sealed class GraphAlgorithmOptions : Serializable

/**
 * PageRank 옵션.
 *
 * @param vertexLabel null 이면 전체 정점 대상.
 * @param edgeLabel null 이면 모든 간선 포함.
 * @param iterations 반복 횟수 (기본 20).
 * @param dampingFactor 감쇠 인수 (기본 0.85). 백엔드별 지원 여부 상이 — §5 매트릭스 참조.
 * @param tolerance 수렴 허용 오차 (기본 1e-4). 백엔드별 지원 여부 상이.
 * @param topK 상위 K개 결과만 반환. `Int.MAX_VALUE` = 전체 반환.
 *
 * 결과 순서: score 내림차순 정렬 보장.
 */
data class PageRankOptions(
    val vertexLabel:   String? = null,
    val edgeLabel:     String? = null,
    val iterations:    Int     = 20,
    val dampingFactor: Double  = 0.85,
    val tolerance:     Double  = 1e-4,
    val topK:          Int     = Int.MAX_VALUE,
) : GraphAlgorithmOptions() {
    companion object { val Default = PageRankOptions() }
}

/**
 * Degree Centrality 옵션.
 *
 * @param edgeLabel null 이면 모든 간선 포함.
 * @param direction 방향 (BOTH/OUTGOING/INCOMING).
 */
data class DegreeOptions(
    val edgeLabel: String?    = null,
    val direction: Direction  = Direction.BOTH,
) : GraphAlgorithmOptions() {
    companion object { val Default = DegreeOptions() }
}

/**
 * Connected Components 옵션.
 *
 * @param vertexLabel null 이면 전체 정점.
 * @param edgeLabel null 이면 모든 간선.
 * @param weakly true = Weakly Connected, false = Strongly Connected.
 * @param minSize 반환할 최소 컴포넌트 크기 (기본 1).
 */
data class ComponentOptions(
    val vertexLabel: String?  = null,
    val edgeLabel:   String?  = null,
    val weakly:      Boolean  = true,
    val minSize:     Int      = 1,
) : GraphAlgorithmOptions() {
    companion object { val Default = ComponentOptions() }
}
```

#### `GraphTraversalOptions` 하위 — traversal (maxDepth 있음)

기존 파일: `graph-core/.../model/GraphTraversalOptions.kt` 에 추가

```kotlin
/**
 * BFS / DFS 공통 옵션.
 *
 * @param edgeLabel null 이면 모든 간선.
 * @param direction 탐색 방향 (기본 OUTGOING).
 * @param maxDepth 최대 탐색 깊이 (기본 5).
 * @param maxVertices 반환할 최대 정점 수 (안전 가드, 기본 10_000).
 */
data class BfsDfsOptions(
    val edgeLabel:   String?    = null,
    val direction:   Direction  = Direction.OUTGOING,
    override val maxDepth: Int  = 5,
    val maxVertices: Int        = 10_000,
) : GraphTraversalOptions() {
    companion object { val Default = BfsDfsOptions() }
}

/**
 * Cycle 탐지 옵션.
 *
 * @param vertexLabel null 이면 전체 정점.
 * @param edgeLabel null 이면 모든 간선.
 * @param maxDepth 순환 경로 최대 길이 (기본 10).
 * @param maxCycles 반환할 최대 순환 수 (기본 100).
 */
data class CycleOptions(
    val vertexLabel: String? = null,
    val edgeLabel:   String? = null,
    override val maxDepth: Int = 10,
    val maxCycles:   Int       = 100,
) : GraphTraversalOptions() {
    companion object { val Default = CycleOptions() }
}
```

### 3.2 신규 결과 모델 (`graph-core/model`)

모든 결과 모델은 `data class : Serializable` + `serialVersionUID = 1L` 패턴을 따른다
(기존 `GraphVertex`, `GraphEdge`, `GraphPath` 동일 패턴).

```kotlin
/**
 * PageRank 점수 한 개.
 *
 * 결과 목록은 score 내림차순 정렬이 보장된다.
 * `Flow<PageRankScore>` 도 동일 순서로 emit 된다.
 */
data class PageRankScore(
    val vertex: GraphVertex,
    val score: Double,
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

/** Degree Centrality 결과. */
data class DegreeResult(
    val vertexId: GraphElementId,
    val inDegree: Int,
    val outDegree: Int,
) : Serializable {
    val total: Int get() = inDegree + outDegree
    companion object { private const val serialVersionUID: Long = 1L }
}

/** 연결 컴포넌트 — 동일 componentId 를 갖는 정점 집합. */
data class GraphComponent(
    val componentId: String,
    val vertices: List<GraphVertex>,
) : Serializable {
    val size: Int get() = vertices.size
    companion object { private const val serialVersionUID: Long = 1L }
}

/** BFS/DFS 방문 이벤트 (탐색 순서 depth 포함). */
data class TraversalVisit(
    val vertex: GraphVertex,
    val depth: Int,
    val parentId: GraphElementId?,
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

/**
 * 탐지된 순환.
 *
 * [path] 의 첫 번째 정점과 마지막 정점은 동일하다 (first == last 보장).
 * [length] 는 [path] 의 간선 수로 계산되는 computed property.
 */
data class GraphCycle(
    val path: GraphPath,
) : Serializable {
    val length: Int get() = path.edges.size
    companion object { private const val serialVersionUID: Long = 1L }
}
```

### 3.3 `GraphAlgorithmRepository` (동기 — 신규 인터페이스, Approach B)

```kotlin
/**
 * 그래프 분석 알고리즘 저장소 (동기 방식).
 *
 * 결과 순서 계약:
 * - [pageRank]: score 내림차순 정렬.
 * - [connectedComponents]: componentId 오름차순, 내부 정점은 임의 순서.
 * - [bfs]: BFS 방문 순서 (레벨 순).
 * - [dfs]: DFS 방문 순서 (깊이 우선).
 * - [detectCycles]: 임의 순서.
 */
interface GraphAlgorithmRepository {

    fun pageRank(options: PageRankOptions = PageRankOptions.Default): List<PageRankScore>

    fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): List<GraphComponent>

    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): List<GraphCycle>
}
```

### 3.4 `GraphSuspendAlgorithmRepository` (코루틴 — 신규 인터페이스)

```kotlin
/**
 * 그래프 분석 알고리즘 저장소 (코루틴/Flow 방식).
 *
 * Flow 순서 계약: [GraphAlgorithmRepository] 의 동기 버전과 동일.
 * [pageRank] Flow 는 score 내림차순으로 emit 된다.
 */
interface GraphSuspendAlgorithmRepository {

    fun pageRank(options: PageRankOptions = PageRankOptions.Default): Flow<PageRankScore>

    suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    fun connectedComponents(options: ComponentOptions = ComponentOptions.Default): Flow<GraphComponent>

    fun bfs(startId: GraphElementId, options: BfsDfsOptions = BfsDfsOptions.Default): Flow<TraversalVisit>

    fun dfs(startId: GraphElementId, options: BfsDfsOptions = BfsDfsOptions.Default): Flow<TraversalVisit>

    fun detectCycles(options: CycleOptions = CycleOptions.Default): Flow<GraphCycle>
}
```

### 3.5 `GraphVirtualThreadAlgorithmRepository` (Virtual Threads — 브릿지 어댑터)

> Java 25 Project Loom. Java 코드와의 상호운용성(`CompletableFuture`) 및 동기 드라이버 위에서 논블로킹 효과를 위해 추가.
> Kotlin 코드에서는 코루틴 API가 우선이며, VT API는 Java 라이브러리/프레임워크 연동 시 사용.

**설계 전략**: 신규 인터페이스를 모든 백엔드에서 구현하지 않고, **`GraphAlgorithmRepository` (동기)를 `Executors.newVirtualThreadPerTaskExecutor()`로 감싸는 브릿지** 방식 채택.
→ 백엔드별 추가 구현 없음. `graph-core` 에 어댑터 한 개로 완결.

```kotlin
/**
 * Virtual Thread 기반 그래프 분석 알고리즘 저장소.
 *
 * Java 코드 또는 CompletableFuture 기반 파이프라인과의 상호운용을 위해 제공된다.
 * Kotlin 코드에서는 [GraphSuspendAlgorithmRepository] 사용을 권장한다.
 *
 * 결과 순서 계약: [GraphAlgorithmRepository] 와 동일.
 */
interface GraphVirtualThreadAlgorithmRepository {

    fun pageRankAsync(options: PageRankOptions = PageRankOptions.Default): CompletableFuture<List<PageRankScore>>

    fun degreeCentralityAsync(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): CompletableFuture<DegreeResult>

    fun connectedComponentsAsync(
        options: ComponentOptions = ComponentOptions.Default,
    ): CompletableFuture<List<GraphComponent>>

    fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>>

    fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>>

    fun detectCyclesAsync(
        options: CycleOptions = CycleOptions.Default,
    ): CompletableFuture<List<GraphCycle>>
}
```

**기본 구현 — `VirtualThreadAlgorithmAdapter`** (`graph-core` 에 위치):

```kotlin
/**
 * [GraphAlgorithmRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * ```kotlin
 * val ops: GraphOperations = Neo4jGraphOperations(driver)
 * val vtOps = ops.asVirtualThread()          // 확장 함수
 * val future = vtOps.pageRankAsync()         // Virtual Thread 실행
 * val scores = future.join()
 * ```
 *
 * @param delegate 위임할 동기 [GraphAlgorithmRepository].
 * @param executor Virtual Thread executor. 기본값은 `Executors.newVirtualThreadPerTaskExecutor()`.
 */
class VirtualThreadAlgorithmAdapter(
    private val delegate: GraphAlgorithmRepository,
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
) : GraphVirtualThreadAlgorithmRepository {

    override fun pageRankAsync(options: PageRankOptions): CompletableFuture<List<PageRankScore>> =
        CompletableFuture.supplyAsync({ delegate.pageRank(options) }, executor)

    override fun degreeCentralityAsync(vertexId: GraphElementId, options: DegreeOptions): CompletableFuture<DegreeResult> =
        CompletableFuture.supplyAsync({ delegate.degreeCentrality(vertexId, options) }, executor)

    override fun connectedComponentsAsync(options: ComponentOptions): CompletableFuture<List<GraphComponent>> =
        CompletableFuture.supplyAsync({ delegate.connectedComponents(options) }, executor)

    override fun bfsAsync(startId: GraphElementId, options: BfsDfsOptions): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.bfs(startId, options) }, executor)

    override fun dfsAsync(startId: GraphElementId, options: BfsDfsOptions): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.dfs(startId, options) }, executor)

    override fun detectCyclesAsync(options: CycleOptions): CompletableFuture<List<GraphCycle>> =
        CompletableFuture.supplyAsync({ delegate.detectCycles(options) }, executor)
}

/** [GraphAlgorithmRepository]를 Virtual Thread 어댑터로 감싸는 확장 함수. */
fun GraphAlgorithmRepository.asVirtualThread(
    executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadAlgorithmRepository = VirtualThreadAlgorithmAdapter(this, executor)
```

**`GraphOperations` 합성 업데이트**:
```kotlin
interface GraphOperations :
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphGenericRepository,                // traversal + algorithm 묶음
    GraphVirtualThreadAlgorithmRepository  // 브릿지 — default impl 위임
```

> **범위 판단**: `GraphVirtualThreadAlgorithmRepository`는 현재 스펙(알고리즘 확장)에만 국한.
> Vertex/Edge/Traversal 전체에 VT API를 확장하는 작업은 별도 TODO로 분리.

---

## 4. 설계 대안 (2-3개 접근 방식 평가)

### Approach A — 단일 인터페이스 확장 (선택 권장)

기존 `GraphTraversalRepository`에 6개 메서드를 직접 추가.
- 장점: 사용자 facade(`GraphOperations`)는 변경 없이 자동 노출. Discovery 우수.
- 단점: 백엔드 필수 구현 부담 ↑. 미지원은 `UnsupportedOperationException`.

### Approach B — `GraphAlgorithmRepository` 분리 + `GraphGenericRepository` 중간 계층 + `GraphOperations` 합성 ✅ 채택

기존 패턴(`GraphOperations`가 여러 repository를 합성)과 동일한 방식으로 확장.
**`GraphGenericRepository`** 중간 인터페이스를 두어 traversal + algorithm을 한 계층으로 묶는다.

```kotlin
// 동기
interface GraphGenericRepository : GraphTraversalRepository, GraphAlgorithmRepository

interface GraphOperations :
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphGenericRepository          // ← traversal + algorithm 묶음

// 코루틴
interface GraphSuspendGenericRepository : GraphSuspendTraversalRepository, GraphSuspendAlgorithmRepository

interface GraphSuspendOperations :
    GraphSuspendSession,
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository,
    GraphSuspendGenericRepository
```

- 장점:
  - 기존 `GraphOperations` 합성 패턴과 완전히 일관성 유지.
  - `GraphGenericRepository` 하나로 traversal + algorithm 모두 접근 → **타입 캐스팅 문제 완화**.
  - 테스트/서비스 코드에서 `GraphOperations` 대신 `GraphGenericRepository`를 파라미터 타입으로 사용 → 의존성 범위 최소화.
  - 향후 `GraphSuspendGenericRepository`만 주입받으면 코루틴 API 전체 사용 가능.
- 단점: 중간 인터페이스 파일 2개(`GraphGenericRepository`, `GraphSuspendGenericRepository`) 추가.

### Approach C — 알고리즘별 plug-in (Provider/Strategy)

```kotlin
interface GraphAlgorithm<O, R> { fun execute(ops: GraphOperations, opts: O): R }
```
- 장점: 백엔드별 최적 구현 외부화 (Neo4j GDS 등 옵셔널 모듈).
- 단점: 사용 복잡도 ↑, KDoc/디스커버리 ↓, 현 패턴과 이질적.

**결론**: **Approach B** (+ `GraphGenericRepository` 중간 계층) 채택.

이유:
1. 기존 `GraphTraversalRepository` 의미가 *traversal* 에 국한되므로 analytics는 별도 책임 분리가 명확.
2. `GraphGenericRepository` 도입으로 타입 캐스팅 없이 두 계층을 함께 사용 가능.
3. 사용자는 여전히 `ops.pageRank(...)` 단일 facade 로 호출 가능.
4. 향후 백엔드별 capability flag (`supportsPageRank()`) 추가 여지 확보.

---

## 5. 백엔드별 구현 가능성 평가

| 알고리즘 | Neo4j (Cypher) | Neo4j + GDS | Memgraph (MAGE) | AGE (PG) | TinkerPop (Gremlin) |
|----------|----------------|-------------|------------------|----------|---------------------|
| pageRank | 직접 어려움 (반복 쿼리) | `gds.pageRank.stream` ★ | `pagerank.get()` MAGE ★ | JVM PageRank after JDBC fetch (topK 강제 권고) | `pageRank()` step ★ |
| degreeCentrality | `MATCH ... RETURN count(*)` ★ | `gds.degree.stream` | `degree_centrality.get()` | Cypher-over-SQL count ★ | `bothE().count()` ★ |
| connectedComponents | 어려움 (재귀 path) | `gds.wcc.stream` ★ | `weakly_connected_components` ★ | JVM Union-Find on edges fetch | `connectedComponent()` step ★ |
| bfs / dfs | `MATCH (a)-[*1..n]->(b)` (BFS만) | `gds.bfs.stream`/`gds.dfs.stream` ★ | MAGE `bfs/dfs` | JVM BFS/DFS after JDBC fetch | `repeat().emit()` BFS, DFS는 path ordering |
| detectCycles | `MATCH p=(a)-[*1..n]->(a) RETURN p` ★ | `gds.alpha.scc` (강결합) | MAGE `cycles` | **JVM Tarjan after JDBC fetch** (AGE variable-length path 바인딩 미검증 → 폴백 사용) | `cyclicPath()` step ★ |

★ = 백엔드 native 지원.

### 5.1 백엔드 채택 전략

- **Neo4j**: 1차 → 순수 Cypher (degree, cycle, bfs/dfs MATCH 기반). 2차 PR에서 GDS optional sub-module 분리.
- **Memgraph**: Neo4j Bolt 드라이버 공유. 1차는 순수 Cypher 폴백. MAGE는 Phase 7(T-22)에서 별도 처리.
- **AGE**: degree는 `cypher()` Cypher-over-SQL. PageRank/CC/BFS/DFS/detectCycles 전부 JVM 폴백 (`algo/internal/`). AGE의 variable-length path 바인딩(`p=(a)-[*]->(a)`)은 현재 AGE 버전에서 미검증 — 안전하게 폴백 사용.
- **TinkerPop**: 모든 알고리즘 native Gremlin step 존재 → 가장 완전한 구현.

### 5.2 폴백 정책

1. 백엔드 native 지원 → 사용.
2. 미지원 → `graph-core/algo/internal/` 의 JVM 폴백 사용 (BFS/DFS/CC/Cycles).
3. PageRank 폴백은 fetch 비용 큼 → AGE의 경우 `topK` 옵션 설정 강력 권고 + 경고 로그.
4. MAGE/GDS 의존 알고리즘은 Phase 7 별도 optional sub-module에서 구현.

### 5.3 Memgraph MAGE 이미지 마이그레이션 계획 (Phase 7)

| 항목 | 현재 | 변경 후 |
|------|------|---------|
| 이미지 | `memgraph/memgraph` (기본) | `memgraph/memgraph-mage:latest` |
| MAGE 라이센스 | — | OSS 버전 포함 (무료) |
| 이미지 크기 | ~200MB | ~1.2GB (예상) |
| 테스트 영향 | 기존 테스트 변경 없음 | `graph-servers/MemgraphServer.kt` 이미지 상수 교체 |
| CI 영향 | 빠름 | pull 시간 증가 → Layer 캐시 필요 |
| 롤백 | — | 이미지 태그를 기본으로 복구 |

Phase 1-5는 MAGE 없이 순수 Cypher로 구현. Phase 7에서 MAGE 이미지 교체 + MAGE 알고리즘 PR 분리.

---

## 6. 파일 변경 / 추가 목록

### 신규
- `graph-core/.../model/GraphAlgorithmOptions.kt`    — `GraphAlgorithmOptions` sealed + `PageRankOptions`, `DegreeOptions`, `ComponentOptions`
- `graph-core/.../model/PageRankScore.kt`
- `graph-core/.../model/DegreeResult.kt`
- `graph-core/.../model/GraphComponent.kt`
- `graph-core/.../model/TraversalVisit.kt`
- `graph-core/.../model/GraphCycle.kt`
- `graph-core/.../repository/GraphAlgorithmRepository.kt`
- `graph-core/.../repository/GraphSuspendAlgorithmRepository.kt`
- `graph-core/.../repository/GraphGenericRepository.kt`        — `GraphTraversalRepository + GraphAlgorithmRepository`
- `graph-core/.../repository/GraphSuspendGenericRepository.kt` — `GraphSuspendTraversalRepository + GraphSuspendAlgorithmRepository`
- `graph-core/.../algo/internal/UnionFind.kt`         (CC 폴백)
- `graph-core/.../algo/internal/CycleDetector.kt`    (Tarjan DFS 기반)
- `graph-core/.../algo/internal/BfsDfsRunner.kt`     (인접 리스트 기반 JVM 폴백)
- `graph-core/.../algo/internal/PageRankCalculator.kt` (반복 수렴 JVM 폴백)
- `graph-core/.../repository/GraphVirtualThreadAlgorithmRepository.kt`
- `graph-core/.../algo/VirtualThreadAlgorithmAdapter.kt`          (브릿지 어댑터 + 확장 함수)

### 수정
- `GraphTraversalOptions.kt` — `BfsDfsOptions`, `CycleOptions` 2개 추가 (sealed 하위).
- `GraphOperations.kt` — `GraphAlgorithmRepository` 직접 합성 → `GraphGenericRepository` 합성으로 교체.
- `GraphSuspendOperations.kt` — 동일 (`GraphSuspendGenericRepository` 합성).
- `Neo4jGraphOperations.kt` / `Neo4jGraphSuspendOperations.kt` — 6 method override.
- `MemgraphGraphOperations*.kt` — 동일.
- `AgeGraphOperations*.kt` — 동일 + AgeSql 헬퍼 확장.
- `TinkerGraphOperations*.kt` — 동일.

### 테스트 (모듈별 Abstract*Test 패턴)
- `AbstractAlgorithmTest`, `AbstractAlgorithmSuspendTest` 추가 (examples 모듈에 두지 않고 graph-* 모듈 테스트로).
- 각 백엔드별 `Neo4j/Memgraph/Tinker/AgeAlgorithmTest` 작성.

---

## 7. 코드 스타일 / 컨벤션 준수

- KDoc: 모든 공개 API/모델 한국어 작성, 사용 예제 fenced code block 포함.
- `KLogging` companion object: **구체 구현 클래스**에만 추가 (`Neo4jAlgorithmRepository` 등). 인터페이스는 제외.
- `requireNotBlank`: non-nullable String 파라미터에 적용. nullable `String?` 파라미터는 blank 입력 시 `null` 로 정규화.
- `Serializable` + `serialVersionUID = 1L` 모델 일관성 유지.
- 코루틴 우선 — `Flow.flowOn(Dispatchers.IO)` 권장 위치 명시.
- `try-catch CancellationException` 금지 (재던짐).

---

## 8. 위험 / 미정 항목 (Open Questions)

1. **GDS 의존**: Neo4j GDS 라이센스(Enterprise)와의 충돌. → optional sub-module `graph-neo4j-gds` 신설 검토.
2. **Memgraph MAGE**: 컨테이너 이미지 (`memgraph/memgraph-mage`) 변경 필요. `graph-servers` 업데이트 범위.
3. **AGE PageRank 폴백 비용**: 대규모 그래프에서 fetch 비용 큼 → topK 옵션 강제 권고 / 경고 로그.
4. **Path 직렬화**: `GraphCycle.path` ↔ `GraphPath` 의 first==last 정점 보장 정책.
5. **API 안정성**: 0.x 단계이므로 binary compat 부담 적음. 1.0 전에 finalize 권장.

---

## 9. 초안 태스크 목록

### Phase 1 — 인터페이스/모델 골격 (graph-core)
- [ ] T-1 모델 5종(`PageRankScore`/`DegreeResult`/`GraphComponent`/`TraversalVisit`/`GraphCycle`) 추가 (`serialVersionUID` 포함)
- [ ] T-2a `GraphAlgorithmOptions` sealed + `PageRankOptions`/`DegreeOptions`/`ComponentOptions` 신규 파일 추가
- [ ] T-2b `BfsDfsOptions`/`CycleOptions` 를 `GraphTraversalOptions.kt` 에 추가 (sealed 하위)
- [ ] T-3 `GraphAlgorithmRepository` / `GraphSuspendAlgorithmRepository` 인터페이스 추가
- [ ] T-4a `GraphGenericRepository` / `GraphSuspendGenericRepository` 중간 합성 인터페이스 추가
- [ ] T-4b `GraphOperations` → `GraphGenericRepository` 합성으로 교체, `GraphSuspendOperations` 동일 적용
- [ ] T-5 `algo/internal/` UnionFind · BfsDfsRunner · CycleDetector · PageRankCalculator 폴백 유틸 + 단위 테스트

### Phase 2 — TinkerPop 구현 (가장 완전 지원)
- [ ] T-6 6개 메서드 native Gremlin step 구현
- [ ] T-7 동기/코루틴 변환 (`flow { ... }.flowOn(Dispatchers.IO)`)
- [ ] T-8 `TinkerGraphAlgorithmTest` (PER_CLASS, 인메모리)

### Phase 3 — Neo4j (Cypher 1차)
- [ ] T-9 degreeCentrality / detectCycles / bfs / dfs Cypher 구현
- [ ] T-10 connectedComponents · pageRank → 폴백 또는 GDS optional 분기
- [ ] T-11 `Neo4jAlgorithmTest` (Testcontainers, Bolt)

### Phase 4 — Memgraph
- [ ] T-12 Neo4j Cypher 코드 재사용 + MAGE 호출 옵션
- [ ] T-13 `MemgraphAlgorithmTest` (이미지 변경 검토)

### Phase 5 — AGE
- [ ] T-14 `AgeSql.kt`에 degree/cycle Cypher-over-SQL 헬퍼 추가
- [ ] T-15 PageRank/CC/BFS/DFS — JDBC fetch + `algo/internal` 호출
- [ ] T-16 `AgeAlgorithmTest` (Testcontainers PostgreSQL+AGE)

### Phase 6 — 통합 / 문서화
- [ ] T-17 `Abstract(Suspend)AlgorithmTest` 공통화 + 4 백엔드 PER_CLASS 구체화
- [ ] T-18 examples 모듈에 알고리즘 사용 예시 추가 (LinkedIn 그래프 — pageRank, connectedComponents)
- [ ] T-19a `graph-core` README.md / README.ko.md — `GraphAlgorithmRepository` 인터페이스 문서 추가
- [ ] T-19b `graph-neo4j` README.md / README.ko.md — Neo4j 알고리즘 지원 매트릭스 추가
- [ ] T-19c `graph-memgraph` README.md / README.ko.md — Memgraph 알고리즘 지원 매트릭스 추가
- [ ] T-19d `graph-age` README.md / README.ko.md — AGE 폴백 정책 문서 추가
- [ ] T-19e `graph-tinkerpop` README.md / README.ko.md — TinkerPop native step 문서 추가
- [ ] T-20 전체 테스트 통과 후 `docs/testlogs/2026-04.md` 맨 위 행에 결과 기록

### Phase 7 — Optional (별도 PR)
- [ ] T-21 `graph-neo4j-gds` sub-module 분리 검토
- [ ] T-22 `graph-memgraph-mage` 이미지 + MAGE Provider

### Phase 8 — Virtual Threads 브릿지 (graph-core)

> Vertex/Edge/Traversal 전체로의 확장은 별도 TODO — 본 Phase는 Algorithm API만.

- [ ] T-23 `GraphVirtualThreadAlgorithmRepository` 인터페이스 추가 (`CompletableFuture<T>` 반환)
- [ ] T-24 `VirtualThreadAlgorithmAdapter` 구현 + `GraphAlgorithmRepository.asVirtualThread()` 확장 함수
- [ ] T-25 `GraphOperations`에 `GraphVirtualThreadAlgorithmRepository` default 구현 합성 추가
- [ ] T-26 `VirtualThreadAlgorithmAdapterTest` 단위 테스트 (TinkerGraph 기반, Docker 불필요)

---

## 10. 롤백 / 부분 릴리스 전략

- **Phase 1-2 완료 시**: TinkerPop 전용 알고리즘 기능으로 부분 릴리스 가능 (인메모리 전용 사용자 대상).
- **Phase 3 실패 시**: `graph-neo4j` 모듈의 새 메서드를 `UnsupportedOperationException` 으로 stub → 나머지 백엔드에 영향 없음.
- **Phase 5 (AGE) 지연 시**: AGE 구현 없이 0.2.x 릴리스 가능. `AgeGraphOperations.pageRank()` stub.
- **인터페이스 변경 시**: 0.x 단계이므로 binary compat 부담 없음. 단, Phase 1 완료 후 인터페이스 서명 동결 권고.
- **모든 Phase 완료 후**: `graph-neo4j-gds` / `graph-memgraph-mage` 는 별도 선택 모듈로 분리 (Phase 7).

## 11. 검증 (Definition of Done)

- 4개 백엔드 모두 빌드 통과: `./gradlew build`.
- 신규 단위/통합 테스트 80%+ 커버리지.
- `Abstract*AlgorithmTest` 4 백엔드 구체 테스트 모두 통과.
- 모든 신규 public API에 한국어 KDoc + 사용 예제.
- `Neo4jGraphOperations` Cypher 인젝션 검토 (label/edgeLabel sanitize).

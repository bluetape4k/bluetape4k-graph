# 가중치 그래프 지원 — Weighted Edge + Dijkstra / A\* Shortest Path 설계

- **Issue**: #31 가중치 그래프 지원 — weighted edge + Dijkstra / A\* 최단경로
- **Status**: Draft (Spec)
- **Author**: bluetape4k-graph maintainers
- **Date**: 2026-04-28
- **관련 모듈**: `graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`, `graph-falkordb`, `examples/*`, `benchmark/graph-benchmark`

---

## 1. 개요 및 목표

### 1.1 배경

`bluetape4k-graph`는 현재 모든 백엔드(Neo4j / Memgraph / AGE / TinkerPop / FalkorDB)에서 `GraphTraversalRepository.shortestPath` 를 **간선 수(hops) 기준 최단 경로**로 정의한다. 즉, 모든 간선의 비용을 1로 가정한 **unweighted shortest path** 만 지원한다.

실제 그래프 응용(도로망, 라우팅, LinkedIn 1‑hop 친밀도, 코드 의존성 가중치 등)에서는 간선마다 비용(거리, 지연, 신뢰도, 비용)이 다른 **가중치 최단 경로** 가 필수다. 본 설계는 다음을 추가한다.

1. **Edge weight** : `GraphEdge.properties` 에 저장된 숫자 속성을 비용으로 사용하는 표준화된 방식.
2. **Dijkstra 최단 경로** : `PathOptions.weightProperty` 가 지정되면 가중치 기반으로 동작.
3. **A\* 최단 경로** : 사용자가 제공하는 휴리스틱 함수로 가속.
4. **`GraphPath.totalWeight`** : 경로의 총 비용 (가중치 미사용 시 hops 와 동일).

### 1.2 목표

- 기존 `shortestPath` 의 **시그니처를 유지** 하되, `PathOptions.weightProperty` 가 `null` 일 때만 기존 unweighted 동작을 보장한다(완전한 backward compatibility).
- 가중치 모드일 때 **모든 백엔드에서 동일한 결과**(같은 총 비용·동치 경로 후보 중 결정적 선택)를 보장한다.
- 백엔드별 native 가중치 알고리즘의 **부재** 를 받아들이고, 첫 단계는 **JVM 폴백 단일 구현** 으로 통일한다.
- A\* 는 별도 메서드 `aStarPath(...)` 로 추가 — 휴리스틱 함수가 그래프 외부 도메인 지식이라서 옵션 객체에 함수 타입을 두는 것보다 별도 메서드가 더 명확하다.
- `GraphSuspendOperations`, `VirtualThreadAdapter` 의 이중 API 패턴을 유지한다.
- 음수 가중치는 **거부(예외)** — Dijkstra 가정 위반이며, Bellman‑Ford 는 본 이슈 범위를 벗어난다.

### 1.3 비목표 (out of scope)

- Bellman‑Ford / Floyd‑Warshall / Johnson 등 음수 가중치 또는 all‑pairs 최단 경로.
- Native GDS/APOC 호출(Neo4j Graph Data Science). 추후 별도 이슈로.
- Yen's k‑shortest paths, weighted `allPaths`. (`allPaths` 는 단순 경로 enumeration 정의 유지)
- 동적 가중치(쿼리 실행 시 평가되는 람다 가중치) — 단순 property 키 기반만 지원.

---

## 2. 브레인스토밍

### 2.1 문제 재정의

> "동일한 출발/도착 사이에서 간선 수가 적은 경로보다 비용 합이 작은 경로가 답이 되어야 한다. 이를 백엔드 추상화를 깨지 않으면서, 다섯 가지 그래프 백엔드에 일관되게, 그리고 결정적인 결과로 제공한다."

**입력 조건**

- `GraphEdge.properties` 의 임의 키(`weight`, `distance`, `cost` 등)가 비용으로 지정될 수 있다.
- 비용 값 타입은 `Number` (`Int`, `Long`, `Double`, `Float`, `BigDecimal`). 내부적으로 `Double` 로 정규화한다.
- 일부 간선이 weight 속성을 갖지 않을 수 있다 → 결측 정책 필요.

**제약**

- 백엔드 native 가중 최단경로 미지원(Cypher native, Gremlin core, AGE, FalkorDB 모두).
- 결과는 결정적이어야 한다(테스트 검증을 위해).
- 메모리 사용량은 graph-core BFS 구현과 비슷한 수준을 목표 — adjacency map 기반.
- 코루틴/Flow 호환, 가상 스레드 어댑터 호환.

**불확실성**

- Neo4j 5+에서 `apoc.algo.dijkstra` 호출 가능 여부 — APOC 플러그인 의존이라서 보장 불가.
- AGE 의 가중치 cypher 지원 — 미지원으로 확인됨.
- `Number → Double` 변환에서 `BigDecimal` overflow 가능성 — 가능성 있으나 일반적 응용에서는 무시 가능.

### 2.2 설계 위험 / 실패 모드

| # | 위험 | 영향 | 완화책 |
|---|------|------|--------|
| R1 | **음수 가중치** 입력 → Dijkstra 무한 루프 / 잘못된 답 | 데이터 정합성 파괴 | 정점/간선 적재 시 검증, `IllegalArgumentException` 즉시 throw |
| R2 | **NaN / 무한대** 가중치 → 비교 연산 불가 | 동일 (NaN 은 우선순위 큐에서 비교 실패) | NaN 은 거부, +Infinity 는 "통과 불가" 로 해석 (단순 무시) |
| R3 | **결측 weight 속성** | 일부 간선만 가중치 가짐 → 잘못된 최단경로 | 결측 정책을 옵션으로 노출: `MissingWeightPolicy { FAIL, SKIP, DEFAULT(value) }`. 기본값 `FAIL` |
| R4 | **거대 그래프** 전체 로딩 → OOM | JVM 폴백이 가장 큰 병목 | `maxDepth` 와 `maxVertices` 로 탐색 영역 제한, 우선순위 큐 결정 시 lazy expansion |
| R5 | **부동소수점 누적 오차** 로 인한 비결정성 | 동등 비용 경로의 불안정한 선택 | 비용 비교 시 vertex ID 를 보조 키로 사용해 tie‑break 결정성 확보 |
| R6 | **자기 루프(self loop)** | Dijkstra 에서 처리 가능하지만, 무한 루프 위험 | adjacency 구성 단계에서 `start == end` 간선 제외 옵션 |
| R7 | **A\* 휴리스틱이 admissible 하지 않음** → 최적 경로 보장 안됨 | 사용자 책임이지만 실수 흔함 | KDoc 에 명시, 디버그 모드에서 sanity check 옵션 (선택) |
| R8 | 동시성 — 여러 Dijkstra 호출이 백엔드 connection 을 공유 | 커넥션 누수, 데드락 | 기존 `GraphSession` 트랜잭션 패턴 재사용, 한 호출당 단일 connection lease |

### 2.3 접근 방식 비교 (3안)

#### 안 A: **백엔드 native + JVM 폴백 혼합**

각 백엔드별 가능한 경우 native 가중치 알고리즘 사용, 불가능하면 JVM 폴백.

- Neo4j: APOC 또는 GDS 라이브러리 호출(`CALL apoc.algo.dijkstra(...)`).
- 나머지(AGE, Memgraph core, TinkerPop core, FalkorDB): JVM 폴백.

**장점**: Neo4j + GDS 환경에서 최고 성능.
**단점**:
- APOC/GDS 가용성에 따라 **결과 결정성 깨짐** (백엔드별 서로 다른 동치 경로 선택).
- Neo4j Caching/Suspend 변형 + native fallback 분기 → 코드 폭발.
- 테스트 매트릭스 폭발(APOC 유/무 경우).
- Memgraph `WSHORTEST` 도 가능하지만 Memgraph 전용 → 다시 분기.

**판정**: 1단계에서는 보류. 2단계 별도 이슈로 기록.

#### 안 B: **JVM 폴백 단일 구현 (선택)**

`graph-core` 에 `DijkstraRunner`, `AStarRunner` 를 두고, 각 백엔드의 `shortestPath` 가중치 모드는 모두 JVM 러너에 위임. adjacency 는 백엔드의 `neighbors()` / 가중치 속성을 통해 lazy 로 가져온다.

**장점**:
- **결과 결정성 보장**: 동일 알고리즘, 동일 tie‑break 규칙.
- 코드 중복 0. graph-core 한 곳만 수정.
- 백엔드 변경 표면 최소(각 backend 의 `shortestPath` 안에서 분기 한 줄).
- 테스트 단순.

**단점**:
- 거대 그래프에서 native GDS 만큼 빠르지는 않다.
- adjacency 를 만들기 위해 N hops 만큼 백엔드 호출 — 네트워크 RTT 비용.

**완화**:
- `maxDepth` 로 탐색 영역 제한 (Issue #31 의 `PathOptions` 가 이미 가짐).
- BatchedNeighborFetch 추후 추가 가능(현 단계 비목표).

**판정**: **채택**. Issue #31 Step 1‑R 결론과 일치하며, 안 A 대비 결정성·구현 비용 trade‑off 가 압도적으로 유리.

#### 안 C: **백엔드별 완전 분리 구현**

각 백엔드 모듈에서 자체적으로 Dijkstra 를 구현하고 `JvmFallback` 도 따로 제공.

**장점**: 백엔드 특이 최적화 가능.
**단점**: 안 B 와 비교해 모든 단점이 더 심각. 결정성, 테스트, 유지보수 모두 악화. **즉시 기각**.

#### 안 D (rejected variant): **`PathOptions` 에 `heuristic` 함수 필드 추가하여 A\* 도 동일 메서드 처리**

```kotlin
data class PathOptions(
    val weightProperty: String? = null,
    val heuristic: ((GraphVertex) -> Double)? = null,
    ...
)
```

**기각 사유**:
- `PathOptions` 는 `Serializable` 데이터 클래스 — 함수 타입 필드는 직렬화 불가능.
- 휴리스틱은 도착 정점에 의존하는 closure 라서 옵션 객체 의미와 어긋남.
- 호출 의도가 명확하지 않음 (`shortestPath` vs `aStarPath`).
- 기각하고 **별도 메서드 `aStarPath(fromId, toId, heuristic, options)` 추가** 로 결정.

### 2.4 채택 결정

- **채택**: 안 B (JVM 폴백 단일 구현) + A\* 별도 메서드.
- 백엔드는 모두 `graph-core` 의 `DijkstraRunner` / `AStarRunner` 에 위임한다.
- `PathOptions` 는 **확장만** 하고 깨지 않는다(`weightProperty`, `missingWeightPolicy`, `defaultWeight`).
- `GraphPath.totalWeight` 는 **계산형 프로퍼티** 가 아니라 **저장 필드** 로 둔다 — 가중치 모드의 결과를 메타데이터로 보존하기 위해.

---

## 3. 선택된 설계

### 3.1 모듈 / 패키지 구조

```
graph/graph-core/
  src/main/kotlin/io/bluetape4k/graph/
    model/
      GraphPath.kt                          (수정: totalWeight 필드 추가)
      GraphTraversalOptions.kt              (수정: PathOptions 확장)
      MissingWeightPolicy.kt                (신규)
    repository/
      GraphTraversalRepository.kt           (수정: aStarPath 추가)
      GraphSuspendTraversalRepository.kt    (수정: aStarPath 추가)
      GraphVirtualThreadTraversalRepository.kt (수정: aStarPath 추가)
    algo/internal/
      DijkstraRunner.kt                     (신규)
      AStarRunner.kt                        (신규)
      WeightExtractor.kt                    (신규: Number → Double 안전 변환 + 결측 정책)
      ShortestPathFallback.kt               (신규: 백엔드 위임용 헬퍼)
```

각 백엔드 모듈(`graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`, `graph-falkordb`)의 `*GraphOperations.shortestPath` 는 다음과 같이 분기한다.

```kotlin
override fun shortestPath(
    fromId: GraphElementId,
    toId: GraphElementId,
    options: PathOptions,
): GraphPath? = when (options.weightProperty) {
    null -> // 기존 unweighted 구현 그대로
    else -> ShortestPathFallback.dijkstra(this, fromId, toId, options)
}

override fun aStarPath(
    fromId: GraphElementId,
    toId: GraphElementId,
    heuristic: (GraphVertex) -> Double,
    options: PathOptions,
): GraphPath? = ShortestPathFallback.aStar(this, fromId, toId, heuristic, options)
```

### 3.2 `PathOptions` 확장

```kotlin
data class PathOptions(
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    /**
     * 간선 비용으로 사용할 property 키. null 이면 기존 unweighted (hop 수) shortest path.
     * 비-null 일 때 Dijkstra 또는 A* (aStarPath 호출 시) 알고리즘이 동작한다.
     */
    val weightProperty: String? = null,
    /**
     * weightProperty 가 결측인 간선을 처리할 정책.
     * weightProperty == null 일 때는 무시된다.
     */
    val missingWeightPolicy: MissingWeightPolicy = MissingWeightPolicy.Fail,
    /**
     * 탐색 방향. unweighted 기존 동작은 BOTH 였으나, 가중치 모드도 동일.
     * (현 인터페이스에 direction 이 없었으므로 신규 필드로 추가; 기본값 BOTH 로 backward compat 유지)
     */
    val direction: Direction = Direction.BOTH,
    /**
     * 탐색 중 방문할 최대 정점 수. 이 값을 초과하면 null 반환.
     * OOM 방지용 안전망. 기본값 100,000.
     * hop 수 기반인 maxDepth 와 달리 실제 방문 정점 수로 제한한다.
     */
    val maxVisited: Int = 100_000,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxVisited > 0) { "maxVisited must be > 0, was $maxVisited" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PathOptions()
    }
}

sealed class MissingWeightPolicy: Serializable {
    /** 결측 시 MissingWeightException 던진다 (기본). */
    data object Fail: MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }
    /** 결측 간선은 그래프에서 없는 것으로 취급한다. */
    data object Skip: MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }
    /**
     * 결측 시 [value] 를 사용한다. value > 0.0 이어야 한다.
     * UseDefault(0.0)은 허용하지 않는다 — zero-cost 간선이 있으면 무한 확장 위험이 있어
     * maxVisited 한도를 초과할 수 있다.
     */
    data class UseDefault(val value: Double): MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
        init { require(value > 0.0 && value.isFinite()) { "default weight must be finite and > 0, was $value" } }
    }
    companion object { private const val serialVersionUID: Long = 1L }
}
```

> **호환성 노트**: `PathOptions` 의 모든 신규 필드는 기본값을 갖는다. 기존 호출 사이트는 컴파일 변경 없이 동작한다. `direction` 필드 추가는 data class 의 `componentN` 변경을 일으키지만 public API 사용자가 destructuring 으로 의존하는 사례는 없다고 판단(`grep` 으로 검증 예정). 만약 발견되면 `direction` 은 추가하지 않고 백엔드별 기본값 BOTH 로 처리한다.

### 3.3 `GraphPath` 확장

```kotlin
data class GraphPath(
    val steps: List<PathStep>,
    /**
     * 가중치 모드 결과의 총 비용. unweighted 경로면 [length] 와 동일한 값을 갖는다.
     * 가중치 미적용으로 계산된 경로의 경우 length.toDouble() 으로 채운다.
     */
    val totalWeight: Double = steps.filterIsInstance<PathStep.EdgeStep>().size.toDouble(),
): Serializable {
    val vertices: List<GraphVertex> get() = steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex }
    val edges: List<GraphEdge> get() = steps.filterIsInstance<PathStep.EdgeStep>().map { it.edge }
    val length: Int get() = edges.size
    val isEmpty: Boolean get() = steps.isEmpty()

    companion object {
        private const val serialVersionUID: Long = 1L
        val EMPTY = GraphPath(emptyList(), 0.0)
        fun of(vararg vertices: GraphVertex): GraphPath =
            GraphPath(vertices.map { PathStep.VertexStep(it) }, totalWeight = 0.0)
    }
}
```

> **호환성 노트**: 두 번째 파라미터에 기본값이 있어 기존 `GraphPath(steps)` 호출은 깨지지 않는다. `componentN` 변경(component2 추가)은 destructuring 깨질 수 있으나, 코드베이스 검색으로 `val (s, _) = path` 사용 사례 없음을 확인 후 진행한다.

### 3.4 인터페이스 추가

> **설계 결정 (Spec Review 반영)**: `aStarPath` default method 는 `GraphTraversalRepository` 가 아닌
> 각 백엔드 `*GraphOperations` 에 `override` 로 직접 구현한다.
> 이유: `ShortestPathFallback` 이 `GraphEdgeRepository`/`GraphVertexRepository` API 를 필요로 하는데
> `GraphTraversalRepository` 는 이를 포함하지 않으며, `GraphOperations` 는 모든 레포지토리를 합성하는 타입이다.
> 따라서 interface default method 방식을 사용하면 타입 불일치로 컴파일 실패가 발생한다.
> 각 구체 클래스가 `override` 하면 `this` 가 `GraphOperations` 타입이므로 문제 없다.

#### `GraphTraversalRepository` — 메서드 시그니처만 추가 (default 구현 없음)

```kotlin
/**
 * 두 정점 사이의 가중치 최단 경로를 A* 알고리즘으로 찾는다.
 *
 * @param heuristic 도착 정점까지의 추정 비용 함수. admissible (실제 비용 이하) 이어야 최적성이 보장된다.
 *                  non-admissible 이어도 결과는 반환되지만 최적이 아닐 수 있다.
 *                  반환값은 반드시 `>= 0.0` 의 유한 Double 이어야 한다 (NaN/Infinity 금지).
 * @param options weightProperty 가 반드시 지정되어야 한다. null 이면 함수 시작 시 IllegalArgumentException.
 *
 * 사용 지침:
 * - 휴리스틱 없이 순수 비용 기반 탐색: `shortestPath(from, to, PathOptions(weightProperty="cost"))` 사용.
 * - 도메인 지식으로 탐색 가속 가능할 때만 `aStarPath` 사용 (예: 지도 유클리드 거리).
 */
fun aStarPath(
    fromId: GraphElementId,
    toId: GraphElementId,
    heuristic: (GraphVertex) -> Double,
    options: PathOptions,
): GraphPath?
```

#### `GraphSuspendTraversalRepository` — suspend heuristic 는 사용하지 않음

```kotlin
/**
 * suspend 변형. heuristic 은 순수 동기 함수여야 한다.
 * (suspend 함수를 heuristic 으로 받으면 DijkstraRunner 동기 루프 내에서 runBlocking 필요 →
 *  dispatcher 차단 위험이 있어 금지)
 */
suspend fun aStarPath(
    fromId: GraphElementId,
    toId: GraphElementId,
    heuristic: (GraphVertex) -> Double,
    options: PathOptions,
): GraphPath?
```

#### `GraphVirtualThreadTraversalRepository` — `aStarPathAsync` (기존 Async suffix 패턴 준수)

```kotlin
fun aStarPathAsync(
    fromId: GraphElementId,
    toId: GraphElementId,
    heuristic: (GraphVertex) -> Double,
    options: PathOptions,
): CompletableFuture<GraphPath?>
```

### 3.5 `DijkstraRunner` 설계

```kotlin
/**
 * Dijkstra 최단 경로 JVM 폴백 러너.
 *
 * adjacency 는 lazy 로 백엔드에서 가져온다(closure).
 * 음수/NaN/Infinity 가중치는 WeightExtractor 단계에서 IllegalArgumentException.
 *
 * KLogging companion 포함 — DEBUG 로그(완료), WARN 로그(maxDepth/maxVisited 초과).
 */
internal object DijkstraRunner {
    private val log = KLogging().logger

    data class Result(
        val path: GraphPath?,
        val visitedCount: Int,
        val truncated: Boolean = false,  // maxVisited 초과로 조기 종료됐으면 true
    )

    /**
     * @param fetchIncident 정점에서 인접 (간선, 이웃 정점) 쌍을 반환.
     *        direction 처리(`OUTGOING`/`INCOMING`/`BOTH`)는 ShortestPathFallback 에서 적용.
     *        결정적 결과를 위해 반환 순서는 vertex ID lexicographic 오름차순 정렬되어야 한다.
     * @param weightOf 간선의 비용 (WeightExtractor 적용 후; SKIP 정책 결측이면 null).
     * @param maxVisited 최대 방문 정점 수. 초과 시 null 반환 (WARN 로그).
     */
    fun run(
        fromId: GraphElementId,
        toId: GraphElementId,
        maxDepth: Int,
        maxVisited: Int,
        fetchIncident: (GraphElementId) -> Sequence<Pair<GraphEdge, GraphVertex>>,
        weightOf: (GraphEdge) -> Double?,
        startVertex: GraphVertex,
    ): Result { ... }
}
```

**핵심 알고리즘**

```
dist[from] = 0.0
PQ = MinHeap<(cost, vertexId, depth, prevEdge?, prevVertexId?)>
PQ.add((0, from, 0, null, null))

while PQ not empty:
    (cost, v, depth, prevEdge, prevV) = PQ.pop()
    if v in settled: continue
    settled.add(v)
    parent[v] = (prevEdge, prevV)
    if settled.size > maxVisited:
        log.warn { "maxVisited($maxVisited) exceeded, stopping search" }
        return Result(null, settled.size, truncated=true)
    if v == to: break
    if depth >= maxDepth:
        log.warn { "maxDepth($maxDepth) reached at vertex $v" }
        continue
    // fetchIncident 는 정렬된 순서로 반환 → cross-backend 결정성 보장
    for (edge, w) in fetchIncident(v).sortedBy { it.second.id.value }:
        weight = weightOf(edge) ?: continue   // SKIP 정책일 때 null
        // WeightExtractor 가 이미 NaN/Infinity/음수를 거부했으므로 assert only
        assert(weight >= 0.0 && weight.isFinite())
        if w in settled: continue
        newCost = cost + weight
        PQ.add((newCost, w.id, depth+1, edge, v))

if to !in settled: return Result(null, settled.size)
log.debug { "Dijkstra: from=$fromId to=$toId visited=${settled.size} totalWeight=${dist[to]}" }
reconstructPath(parent, from, to) → Result(GraphPath(steps, totalWeight=dist[to]), settled.size)
```

**Tie‑break**: `fetchIncident` 결과를 vertex ID lexicographic 오름차순 정렬 후 삽입하여 동비용 경로 중 결정적 선택 보장. PQ Comparator 는 `comparingDouble(cost).thenComparing(vertexId)` 로 이중 보장.

**구현 디테일**

- `java.util.PriorityQueue` 사용. `Comparator.comparingDouble<…>(cost).thenComparing(vertexId)`.
- `settled` 를 `HashSet<GraphElementId>` 로 관리.
- `parent` 를 `HashMap<GraphElementId, Pair<GraphEdge, GraphElementId>>`.
- 경로 복원 시 `from→to` 를 list 로 역추적 후 reverse.

### 3.6 `AStarRunner` 설계

Dijkstra 와 거의 동일하지만 PQ 키가 `f = g + h` 이고, `g[v]` 와 `closed` 를 별도로 관리한다.
heuristic 반환값은 `ShortestPathFallback.aStar` 에서 이미 NaN/Infinity/음수를 검증한 `safeHeuristic` 으로 래핑하여 전달된다. **non‑admissible** 이어도 종료는 보장한다(다만 최적이 아닐 수 있음).

```kotlin
/**
 * A* 최단 경로 JVM 폴백 러너.
 * KLogging companion 포함 — DEBUG (완료), WARN (maxDepth/maxVisited 초과).
 */
internal object AStarRunner {
    private val log = KLogging().logger

    fun run(
        fromId: GraphElementId,
        toId: GraphElementId,
        maxDepth: Int,
        maxVisited: Int,
        fetchIncident: (GraphElementId) -> Sequence<Pair<GraphEdge, GraphVertex>>,
        weightOf: (GraphEdge) -> Double?,
        heuristic: (GraphVertex) -> Double,   // 이미 검증된 safeHeuristic 전달
        startVertex: GraphVertex,
    ): DijkstraRunner.Result { ... }
}
```

> 두 러너는 50% 공통 로직(경로 복원, settled 관리)을 가진다. 내부 `PathReconstructor` 헬퍼로 공유.

### 3.7 `WeightExtractor` 설계

```kotlin
internal object WeightExtractor {
    /**
     * 간선에서 비용 추출.
     *
     * 검증 순서:
     * 1. 결측 여부 → policy 적용
     * 2. Number 타입 확인
     * 3. BigDecimal/BigInteger overflow → Infinity 방지 (Double.MAX_VALUE 초과 시 예외)
     * 4. `toDouble()` 변환
     * 5. NaN 거부
     * 6. +Infinity / -Infinity 거부 (isFinite() 검사)
     * 7. 음수 거부
     *
     * @return 비용 (>= 0, finite). SKIP 정책 + 결측 시 null.
     * @throws MissingWeightException Fail 정책 + 결측 시
     * @throws IllegalArgumentException NaN / Infinity / 음수 / 타입 불일치
     */
    fun extract(edge: GraphEdge, key: String, policy: MissingWeightPolicy): Double? {
        val raw = edge.properties[key]
        return when {
            raw == null -> when (policy) {
                MissingWeightPolicy.Fail ->
                    throw MissingWeightException(edge.id, key)
                MissingWeightPolicy.Skip -> null
                is MissingWeightPolicy.UseDefault -> policy.value
            }
            raw is Number -> {
                // BigDecimal/BigInteger overflow 방지: toDouble() 전 범위 검사
                if (raw is java.math.BigDecimal) {
                    require(raw.abs() <= java.math.BigDecimal(Double.MAX_VALUE)) {
                        "edge ${edge.id} weight '$key' BigDecimal overflow: $raw"
                    }
                }
                if (raw is java.math.BigInteger) {
                    require(raw.abs() <= java.math.BigInteger.valueOf(Long.MAX_VALUE)) {
                        "edge ${edge.id} weight '$key' BigInteger overflow: $raw"
                    }
                }
                val d = raw.toDouble()
                require(!d.isNaN()) { "edge ${edge.id} weight '$key' is NaN" }
                require(d.isFinite()) { "edge ${edge.id} weight '$key' is Infinite: $d" }
                require(d >= 0.0) { "edge ${edge.id} weight '$key' is negative: $d" }
                d
            }
            else -> error("edge ${edge.id} weight '$key' is not Number: ${raw::class}")
        }
    }
}

/** Fail 정책 결측 시 전용 예외 — 로그 집계 및 모니터링 알림 설정 가능. */
class MissingWeightException(
    val edgeId: GraphElementId,
    val key: String,
) : IllegalStateException("edge $edgeId missing weight property '$key'")
```

### 3.8 `ShortestPathFallback` (백엔드 위임 헬퍼)

`GraphOperations` (= `GraphVertexRepository` + `GraphEdgeRepository` + 모든 레포지토리 합성 타입) 를 파라미터로 받는다.
인터페이스 default method 대신 각 백엔드 `*GraphOperations.aStarPath` 의 `override` 에서 직접 호출한다.

```kotlin
internal object ShortestPathFallback {
    private val log = KLogging().logger

    /**
     * Dijkstra 위임.
     * ops 는 GraphVertexRepository + GraphEdgeRepository 를 구현한 타입이어야 한다.
     */
    fun dijkstra(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val weightKey = requireNotNull(options.weightProperty) { "weightProperty must be set for Dijkstra" }
        // label 없이 ID 만으로 조회 — GraphVertexRepository 의 findById(id) API 사용
        // (label+id 2-파라미터 API 가 아니라 ID-only lookup 이 필요하면 backend가 구현해야 함)
        val start = ops.findVertexById(fromId) ?: return null
        ops.findVertexById(toId) ?: return null

        log.debug { "[Dijkstra JVM fallback] from=$fromId to=$toId weightKey=$weightKey direction=${options.direction}" }

        val fetchIncident: (GraphElementId) -> Sequence<Pair<GraphEdge, GraphVertex>> = { id ->
            // GraphEdgeRepository API 를 사용해 direction 별 incident 간선 로드
            // OUTGOING: findEdgesByStartVertex(id, edgeLabel) → (edge, endVertex)
            // INCOMING: findEdgesByEndVertex(id, edgeLabel) → (edge, startVertex)
            // BOTH: 위 두 결과 연결 (중복 정점 id 로 dedup)
            buildSequence {
                if (options.direction == Direction.OUTGOING || options.direction == Direction.BOTH) {
                    yieldAll(ops.findEdgesByStartId(id, options.edgeLabel).map { e ->
                        e to (ops.findVertexById(e.endId) ?: return@map null)
                    }.filterNotNull())
                }
                if (options.direction == Direction.INCOMING || options.direction == Direction.BOTH) {
                    yieldAll(ops.findEdgesByEndId(id, options.edgeLabel).map { e ->
                        e to (ops.findVertexById(e.startId) ?: return@map null)
                    }.filterNotNull())
                }
            }
        }
        val weightOf: (GraphEdge) -> Double? = {
            WeightExtractor.extract(it, weightKey, options.missingWeightPolicy)
        }

        return DijkstraRunner.run(
            fromId, toId, options.maxDepth, options.maxVisited, fetchIncident, weightOf, start
        ).path
    }

    fun aStar(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        heuristic: (GraphVertex) -> Double,
        options: PathOptions,
    ): GraphPath? {
        requireNotNull(options.weightProperty) { "weightProperty must be set for A*" }
        val start = ops.findVertexById(fromId) ?: return null
        ops.findVertexById(toId) ?: return null

        // heuristic 반환값 래핑 — NaN / Infinity / 음수 검증
        val safeHeuristic: (GraphVertex) -> Double = { v ->
            heuristic(v).also { h ->
                require(h.isFinite() && h >= 0.0) {
                    "heuristic must return finite non-negative value, got $h for vertex ${v.id}"
                }
            }
        }
        val fetchIncident = buildFetchIncident(ops, options)
        val weightOf: (GraphEdge) -> Double? = {
            WeightExtractor.extract(it, options.weightProperty!!, options.missingWeightPolicy)
        }
        return AStarRunner.run(
            fromId, toId, options.maxDepth, options.maxVisited,
            fetchIncident, weightOf, safeHeuristic, start
        ).path
    }

    private fun buildFetchIncident(
        ops: GraphOperations,
        options: PathOptions,
    ): (GraphElementId) -> Sequence<Pair<GraphEdge, GraphVertex>> = { id ->
        buildSequence {
            if (options.direction == Direction.OUTGOING || options.direction == Direction.BOTH) {
                yieldAll(ops.findEdgesByStartId(id, options.edgeLabel).map { e ->
                    e to (ops.findVertexById(e.endId) ?: return@map null)
                }.filterNotNull())
            }
            if (options.direction == Direction.INCOMING || options.direction == Direction.BOTH) {
                yieldAll(ops.findEdgesByEndId(id, options.edgeLabel).map { e ->
                    e to (ops.findVertexById(e.startId) ?: return@map null)
                }.filterNotNull())
            }
        }
    }
}
```

> **신규 `GraphEdgeRepository` 메서드 필요**: `findEdgesByStartId(id, edgeLabel?)` 과 `findEdgesByEndId(id, edgeLabel?)`.
> 현재 `GraphEdgeRepository` 에 vertex-scoped edge 조회 API 가 없으므로 이를 추가해야 한다.
> 이는 `graph-core` 인터페이스 + 모든 백엔드 구현 변경을 수반한다. Task 목록에 반영.

### 3.9 백엔드별 위임 전략 (요약)

| 백엔드 | unweighted 경로 (현재 유지) | 가중치 / A\* 경로 (신규) |
|--------|----------------------------|-------------------------|
| Neo4j | Cypher `shortestPath()` | `ShortestPathFallback` |
| Memgraph | Cypher BFS + `ORDER BY length(p) LIMIT 1` | `ShortestPathFallback` |
| AGE | AGE Cypher subset BFS | `ShortestPathFallback` |
| TinkerPop | Gremlin `path()` | `ShortestPathFallback` |
| FalkorDB | openCypher BFS | `ShortestPathFallback` |
| Caching\* (각 backend) | upstream 위임 | upstream 위임 (그대로 통과) |

**Suspend / VirtualThread 어댑터**: 동기 구현을 `withContext(Dispatchers.IO)` / `CompletableFuture.supplyAsync(virtualExecutor)` 로 래핑. 휴리스틱이 suspend 인 경우, 어댑터에서 동기 람다로 `runBlocking` 변환 후 동기 러너에 전달(가상 스레드라서 안전).

### 3.10 결정성 / 에러 정책 요약

- 음수 weight → `IllegalArgumentException`
- NaN weight → `IllegalArgumentException`
- +Infinity / -Infinity weight → `IllegalArgumentException` (isFinite() 검사 추가)
- BigDecimal/BigInteger overflow → `IllegalArgumentException` (toDouble() 전 범위 검사)
- 결측 weight + Fail → `MissingWeightException` (IllegalStateException 하위)
- weightProperty 가 `aStarPath` 에 null → `IllegalArgumentException("weightProperty required for aStarPath")`
- maxDepth 음수 → `IllegalArgumentException` (data class init 에서)
- maxVisited 0 이하 → `IllegalArgumentException` (data class init 에서)
- heuristic 이 NaN/Infinity/음수 반환 → `IllegalArgumentException` (ShortestPathFallback 에서 래핑 검증)
- UseDefault(0.0) → `IllegalArgumentException` (MissingWeightPolicy.UseDefault init 에서)
- from 또는 to 가 존재하지 않음 → `null` 반환 (현재 unweighted 와 동일 정책 유지)

---

## 4. 구현 범위

### 4.1 신규 파일

| 경로 | 책임 |
|------|------|
| `graph-core/.../model/MissingWeightPolicy.kt` | 결측 정책 sealed class + MissingWeightException |
| `graph-core/.../algo/internal/DijkstraRunner.kt` | Dijkstra 알고리즘 (KLogging 포함) |
| `graph-core/.../algo/internal/AStarRunner.kt` | A\* 알고리즘 (KLogging 포함) |
| `graph-core/.../algo/internal/WeightExtractor.kt` | weight Number → Double 변환 + 정책 (BigDecimal overflow + isFinite 포함) |
| `graph-core/.../algo/internal/ShortestPathFallback.kt` | 백엔드 위임 헬퍼 (KLogging + direction-aware fetchIncident 포함) |
| `graph-core/.../algo/internal/PathReconstructor.kt` | 두 러너 공유 경로 복원 |

### 4.2 수정 파일

| 경로 | 변경 |
|------|------|
| `graph-core/.../model/GraphPath.kt` | `totalWeight: Double` 필드 추가, `EMPTY`/`of(...)` 갱신 |
| `graph-core/.../model/GraphTraversalOptions.kt` | `PathOptions` 에 `weightProperty`, `missingWeightPolicy`, `direction`, `maxVisited` |
| `graph-core/.../repository/GraphEdgeRepository.kt` | `findEdgesByStartId(id, edgeLabel?)` + `findEdgesByEndId(id, edgeLabel?)` 추가 |
| `graph-core/.../repository/GraphSuspendEdgeRepository.kt` | 동일 suspend 버전 |
| `graph-core/.../repository/GraphTraversalRepository.kt` | `aStarPath` 시그니처 추가 (default 구현 없음) |
| `graph-core/.../repository/GraphSuspendTraversalRepository.kt` | `aStarPath` suspend 시그니처 추가 |
| `graph-core/.../repository/GraphVirtualThreadTraversalRepository.kt` | `aStarPathAsync` 시그니처 추가 |
| `graph-core/.../vt/VirtualThreadTraversalAdapter.kt` | `aStarPathAsync` 위임 구현 |
| `graph-neo4j/.../Neo4jGraphOperations.kt` | `shortestPath` 분기, `aStarPath` override, `findEdgesByStartId`/`findEdgesByEndId` 구현 |
| `graph-neo4j/.../Neo4jGraphSuspendOperations.kt` | 동일 suspend 버전 |
| `graph-neo4j/.../CachingNeo4jGraphOperations.kt` | upstream 위임만 |
| `graph-memgraph/.../*GraphOperations.kt` | 위와 동일 패턴 |
| `graph-age/.../*GraphOperations.kt` | 위와 동일 패턴 |
| `graph-tinkerpop/.../*GraphOperations.kt` | 위와 동일 패턴 |
| `graph-falkordb/.../*GraphOperations.kt` | 위와 동일 패턴 |

### 4.3 테스트 파일

| 경로 | 내용 |
|------|------|
| `graph-core/src/test/.../algo/internal/DijkstraRunnerTest.kt` | 단위: 기본/타이브레이크/maxDepth/maxVisited/결측 정책/음수/NaN/Infinity |
| `graph-core/src/test/.../algo/internal/AStarRunnerTest.kt` | 단위: admissible/non-admissible 휴리스틱, Dijkstra 일치 검증, 잘못된 heuristic(NaN/음수) 거부 |
| `graph-core/src/test/.../algo/internal/WeightExtractorTest.kt` | 단위: Int/Long/Double/Float/BigDecimal/BigInteger 변환, Infinity 거부, BigDecimal overflow 거부, 결측 정책 3종 |
| `graph-core/src/test/.../model/GraphPathSerializationTest.kt` | totalWeight 직렬화 라운드트립 |
| `graph-{neo4j,memgraph,age,tinkerpop,falkordb}/src/test/.../*WeightedShortestPathTest.kt` | 백엔드별 통합. 동일 그래프, 동일 결과 검증 |
| 위와 같은 `*Suspend*Test.kt` | 코루틴 변형 |
| `examples/code-graph-examples/.../Abstract*WeightedTest.kt` | (선택) 가중치 예시 |
| `benchmark/graph-benchmark/.../WeightedShortestPathBench.kt` | JMH: V=1e3/1e4, E=avg 5, weighted vs unweighted 비교 |

### 4.4 문서

- `README.md` / `README.ko.md` 가중치 사용 예시 섹션 추가.
- `docs/superpowers/wiki/weighted-graph-usage.md` 사용 가이드(선택, 별도 PR 가능).
- `CHANGELOG.md` 항목.

---

## 5. Definition of Done (DoD)

### 5.1 기능

- [ ] `PathOptions(weightProperty = "weight")` 로 모든 백엔드에서 가중치 최단 경로가 반환된다.
- [ ] 모든 백엔드에서 동일 그래프 / 동일 옵션 → 동일 `GraphPath` (vertices id 순서 + totalWeight) 반환을 통합 테스트로 보장.
- [ ] `aStarPath(...)` 가 admissible 휴리스틱일 때 Dijkstra 와 동일 결과를 낸다 (테스트 검증).
- [ ] `GraphPath.totalWeight` 가 정확히 비용의 합이며, unweighted 경로일 때는 length 와 동일.
- [ ] 음수/NaN/결측(Fail) 입력에 대해 명시된 예외가 던져진다.
- [ ] Suspend 변형, VirtualThread 어댑터 모두 위 기능을 동일하게 노출한다.

### 5.2 품질

- [ ] `./gradlew build` 통과 (전체).
- [ ] `./gradlew test` 통과.
- [ ] 신규 코드 커버리지 ≥ 80% (DijkstraRunner / AStarRunner / WeightExtractor 는 95%+ 목표).
- [ ] 기존 `shortestPath` 동작 회귀 없음(unweighted 결과 그대로).
- [ ] Kotlin 2.3, Java 25 preview 환경 빌드 성공.
- [ ] ktlint / detekt 통과.

### 5.3 벤치마크

- [ ] `WeightedShortestPathBench` JMH 결과를 `docs/benchmark/` 에 기록.
- [ ] V=1000, E=avg 5 그래프에서 Dijkstra latency p50 / p95 측정.
- [ ] 동일 조건 unweighted BFS 와 비교 표 첨부.

### 5.4 문서

- [ ] `README` 의 사용 예시에 weighted 한 블록 추가.
- [ ] `CHANGELOG.md` 0.x.y 섹션에 항목 추가.
- [ ] KDoc 으로 모든 신규 public 심볼 문서화 (Kotlin coding style 규칙 준수).

---

## 6. 테스트 계획

### 6.1 단위 테스트 (graph-core)

`DijkstraRunnerTest`:
- 직선 그래프 A→B→C (weight 1, 2): totalWeight=3, 경로 [A,B,C].
- 분기 그래프 A→B→D vs A→C→D (각 1+1=2, 1.5+0.4=1.9): A→C→D 선택.
- 동등 비용 분기 — 결정성: vertex id lexicographic 작은 쪽 선택.
- self loop 무시.
- 도달 불가 → null.
- maxDepth 초과 → null.
- 음수 가중치 입력 → IllegalArgumentException.
- NaN 가중치 → IllegalArgumentException.
- 결측 + Fail → IllegalStateException.
- 결측 + Skip → 해당 간선 제외하고 계산.
- 결측 + UseDefault(5.0) → 5.0 사용.

`AStarRunnerTest`:
- admissible heuristic (h=0): Dijkstra 와 동일 결과.
- admissible heuristic (h=직선거리): 동일 결과 + visited count 감소.
- non-admissible heuristic: 결과 다를 수 있음을 명시적으로 표현.
- maxDepth 동일 동작.

`WeightExtractorTest`:
- Int / Long / Double / Float / BigDecimal → Double 변환.
- String → Number 캐스트 실패 시 IllegalStateException.
- 정책 3종 분기.

### 6.2 통합 테스트 (백엔드별)

각 백엔드에서 다음 시나리오를 동일 코드로 실행하는 `Abstract*WeightedShortestPathTest`:

```
A --1--> B --2--> D
A --3--> C --1--> D
A --1--> E (dead-end)
```

- `shortestPath(A, D, weightProperty="weight")` → A→B→D, totalWeight=3
- `shortestPath(A, D, weightProperty="weight", maxDepth=1)` → null
- `aStarPath(A, D, h={ if(it==D) 0.0 else 1.0 }, opts)` → A→B→D
- 결측 정책별 동작
- 동일 그래프에 대해 모든 백엔드에서 동일 결과 (cross-backend equality assertion)

### 6.3 회귀 테스트

- 기존 `shortestPath` 호출(weightProperty 없음)은 hop 수 기준 동작 유지.
- `GraphPath.equals` / `hashCode` / 직렬화 라운드트립.

### 6.4 벤치마크 (graph-benchmark)

- `WeightedDijkstraBench`: 1k / 10k vertex 무작위 그래프, edge density avg 5.
- `UnweightedBfsBench` 기존 비교군.
- Sync vs VirtualThread vs Suspend 3축.

---

## 7. 마이그레이션 / 호환성

- **API 추가만**, 기존 시그니처 무변경.
- `GraphPath` 의 `componentN` 변경 가능성 — 코드베이스 grep 으로 destructuring 사용처 0 확인 후 진행. 사용처 발견 시 `totalWeight` 를 `data class` 본체가 아닌 별도 백킹 필드 + `copy(steps=...)` 패턴으로 우회.
- `PathOptions` 도 동일하게 검증.
- Spring Boot starter 자동 설정은 변경 없음 (인터페이스 default method 가 흡수).

---

## 8. 미해결 / Follow‑up

- (별도 이슈) Neo4j GDS / APOC 가용 시 native 가속.
- (별도 이슈) Memgraph `WSHORTEST` Cypher 절 활용.
- (별도 이슈) Yen's k‑shortest paths.
- (별도 이슈) Bellman‑Ford 음수 가중치 지원.
- (별도 이슈) 큰 그래프용 batched neighbor fetch 최적화.

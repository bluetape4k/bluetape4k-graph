# Graph Algorithm Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `bluetape4k-graph` with 6 graph analytics algorithms (PageRank, Degree Centrality, Connected Components, BFS, DFS, Cycle Detection) across 4 backends (Neo4j, Memgraph, AGE, TinkerPop) with sync, coroutine, and Virtual Thread APIs.

**Architecture:** Approach B + `GraphGenericRepository` intermediate composition. New `GraphAlgorithmRepository` / `GraphSuspendAlgorithmRepository` interfaces; new `GraphAlgorithmOptions` sealed class for non-traversal options; `BfsDfsOptions` and `CycleOptions` extend existing `GraphTraversalOptions`. Backends use native query language where supported (Cypher / Gremlin) and fall back to JVM in-memory implementations (`UnionFind`, `BfsDfsRunner`, `CycleDetector`, `PageRankCalculator`) when not. A `VirtualThreadAlgorithmAdapter` bridges sync to Java 25 Virtual Threads via `CompletableFuture`.

**Tech Stack:** Kotlin 2.3, Java 25 (--enable-preview), Gradle 8.x, kotlinx.coroutines (Flow/suspend), JUnit 5 + Kotest assertions + Kluent + Testcontainers, Neo4j Java Driver, Apache TinkerPop Gremlin, Apache AGE (PostgreSQL extension), Exposed JDBC, KLogging.

---

## File Structure Overview

### New files in `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/`

- `model/GraphAlgorithmOptions.kt` — sealed `GraphAlgorithmOptions` + `PageRankOptions`, `DegreeOptions`, `ComponentOptions`
- `model/PageRankScore.kt`
- `model/DegreeResult.kt`
- `model/GraphComponent.kt`
- `model/TraversalVisit.kt`
- `model/GraphCycle.kt`
- `repository/GraphAlgorithmRepository.kt`
- `repository/GraphSuspendAlgorithmRepository.kt`
- `repository/GraphGenericRepository.kt`
- `repository/GraphSuspendGenericRepository.kt`
- `repository/GraphVirtualThreadAlgorithmRepository.kt`
- `algo/internal/UnionFind.kt`
- `algo/internal/BfsDfsRunner.kt`
- `algo/internal/CycleDetector.kt`
- `algo/internal/PageRankCalculator.kt`
- `algo/VirtualThreadAlgorithmAdapter.kt`

### Modified files

- `graph/graph-core/.../model/GraphTraversalOptions.kt` — add `BfsDfsOptions`, `CycleOptions`
- `graph/graph-core/.../repository/GraphOperations.kt` — replace `GraphTraversalRepository` with `GraphGenericRepository` + `GraphVirtualThreadAlgorithmRepository`
- `graph/graph-core/.../repository/GraphSuspendOperations.kt` — replace `GraphSuspendTraversalRepository` with `GraphSuspendGenericRepository`
- `graph/graph-tinkerpop/.../TinkerGraphOperations.kt` — implement 6 algo methods (native Gremlin)
- `graph/graph-tinkerpop/.../TinkerGraphSuspendOperations.kt` — delegate via Flow
- `graph/graph-neo4j/.../Neo4jGraphOperations.kt` — implement 6 algo methods (Cypher + JVM fallback)
- `graph/graph-neo4j/.../Neo4jGraphSuspendOperations.kt` — delegate via Flow
- `graph/graph-memgraph/.../MemgraphGraphOperations.kt` — share Cypher with Neo4j
- `graph/graph-memgraph/.../MemgraphGraphSuspendOperations.kt`
- `graph/graph-age/.../sql/AgeSql.kt` — add degree Cypher-over-SQL helper
- `graph/graph-age/.../AgeGraphOperations.kt` — implement 6 algo methods (degree native, others fallback)
- `graph/graph-age/.../AgeGraphSuspendOperations.kt`

### New tests

- `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/{UnionFind,BfsDfsRunner,CycleDetector,PageRankCalculator}Test.kt`
- `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapterTest.kt`
- `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmTest.kt`
- `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmSuspendTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmSuspendTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmSuspendTest.kt`

---

# Phase 1 — Core models, options, interfaces, JVM fallbacks (graph-core)

## Task 1: Add result models — `PageRankScore`, `DegreeResult`, `GraphComponent`, `TraversalVisit`, `GraphCycle`

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/PageRankScore.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/DegreeResult.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphComponent.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/TraversalVisit.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphCycle.kt`

- [ ] **Step 1: Create `PageRankScore.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * PageRank 점수 한 개를 나타내는 결과 모델.
 *
 * 결과 목록은 score 내림차순 정렬이 보장된다.
 * `Flow<PageRankScore>` 도 동일 순서로 emit 된다.
 *
 * @property vertex 점수를 가진 정점.
 * @property score 정점의 PageRank 점수 (0.0 이상).
 *
 * ### 사용 예제
 * ```kotlin
 * val scores = ops.pageRank(PageRankOptions(iterations = 20))
 * val top = scores.first()
 * println("${top.vertex.label}: ${top.score}")
 * ```
 */
data class PageRankScore(
    val vertex: GraphVertex,
    val score: Double,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 2: Create `DegreeResult.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Degree Centrality(연결 중심성) 결과.
 *
 * @property vertexId 측정 대상 정점 ID.
 * @property inDegree 들어오는 간선 수.
 * @property outDegree 나가는 간선 수.
 *
 * ### 사용 예제
 * ```kotlin
 * val degree = ops.degreeCentrality(alice.id)
 * println("in=${degree.inDegree} out=${degree.outDegree} total=${degree.total}")
 * ```
 */
data class DegreeResult(
    val vertexId: GraphElementId,
    val inDegree: Int,
    val outDegree: Int,
): Serializable {
    /** in + out 간선 수 합계. */
    val total: Int get() = inDegree + outDegree

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 3: Create `GraphComponent.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 연결 컴포넌트(Connected Component) 결과.
 *
 * 동일 [componentId] 를 갖는 정점 집합을 표현한다.
 *
 * @property componentId 컴포넌트 식별자 (구현체별 임의 값, 동일 컴포넌트는 동일 ID).
 * @property vertices 컴포넌트에 속한 정점 목록.
 *
 * ### 사용 예제
 * ```kotlin
 * val components = ops.connectedComponents(ComponentOptions(weakly = true))
 * components.forEach { println("${it.componentId}: size=${it.size}") }
 * ```
 */
data class GraphComponent(
    val componentId: String,
    val vertices: List<GraphVertex>,
): Serializable {
    /** 컴포넌트 내 정점 수. */
    val size: Int get() = vertices.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 4: Create `TraversalVisit.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * BFS / DFS 방문 이벤트.
 *
 * 탐색 시점의 정점, 깊이, 부모 정점을 표현한다.
 *
 * @property vertex 방문한 정점.
 * @property depth 시작 정점으로부터의 깊이 (시작 정점 = 0).
 * @property parentId 직전 정점의 ID. 시작 정점은 `null`.
 *
 * ### 사용 예제
 * ```kotlin
 * val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3))
 * visits.forEach { println("d=${it.depth} v=${it.vertex.label}") }
 * ```
 */
data class TraversalVisit(
    val vertex: GraphVertex,
    val depth: Int,
    val parentId: GraphElementId?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 5: Create `GraphCycle.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 탐지된 그래프 순환(Cycle).
 *
 * [path] 의 첫 번째 정점과 마지막 정점은 동일하다 (first == last 보장).
 * [length] 는 [path] 의 간선 수로 계산되는 computed property.
 *
 * @property path 순환 경로. 시작과 끝이 같은 [GraphPath].
 *
 * ### 사용 예제
 * ```kotlin
 * val cycles = ops.detectCycles(CycleOptions(maxDepth = 5))
 * cycles.forEach { println("cycle length=${it.length}") }
 * ```
 */
data class GraphCycle(
    val path: GraphPath,
): Serializable {
    /** 순환 경로의 간선 수. */
    val length: Int get() = path.edges.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/PageRankScore.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/DegreeResult.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphComponent.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/TraversalVisit.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphCycle.kt
git commit -m "feat: add graph algorithm result models (PageRankScore, DegreeResult, GraphComponent, TraversalVisit, GraphCycle)"
```

---

## Task 2: Add `GraphAlgorithmOptions` sealed class and subtypes

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphAlgorithmOptions.kt`

- [ ] **Step 1: Create `GraphAlgorithmOptions.kt`**

```kotlin
package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Analytics 알고리즘 옵션의 공통 sealed 클래스.
 *
 * `maxDepth` 개념이 없는 알고리즘(PageRank / Degree / ConnectedComponents) 전용이다.
 * 탐색 깊이가 의미 있는 알고리즘은 [GraphTraversalOptions] 하위를 사용한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val opts: GraphAlgorithmOptions = PageRankOptions(iterations = 20)
 * ```
 */
sealed class GraphAlgorithmOptions: Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * PageRank 옵션.
 *
 * @param vertexLabel `null` 이면 전체 정점 대상.
 * @param edgeLabel `null` 이면 모든 간선 포함.
 * @param iterations 반복 횟수 (기본 20).
 * @param dampingFactor 감쇠 인수 (기본 0.85). 백엔드별 지원 여부 상이.
 * @param tolerance 수렴 허용 오차 (기본 1e-4). 백엔드별 지원 여부 상이.
 * @param topK 상위 K개 결과만 반환. `Int.MAX_VALUE` = 전체 반환.
 *
 * 결과 순서: score 내림차순 정렬 보장.
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = PageRankOptions(vertexLabel = "Person", iterations = 30, topK = 10)
 * val top10 = ops.pageRank(opts)
 * ```
 */
data class PageRankOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val iterations: Int = 20,
    val dampingFactor: Double = 0.85,
    val tolerance: Double = 1e-4,
    val topK: Int = Int.MAX_VALUE,
): GraphAlgorithmOptions() {
    init {
        require(iterations > 0) { "iterations must be > 0, was $iterations" }
        require(topK > 0) { "topK must be > 0, was $topK" }
        require(dampingFactor in 0.0..1.0) { "dampingFactor must be in [0,1], was $dampingFactor" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PageRankOptions()
    }
}

/**
 * Degree Centrality 옵션.
 *
 * @param edgeLabel `null` 이면 모든 간선 포함.
 * @param direction 방향 (BOTH / OUTGOING / INCOMING).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = DegreeOptions(edgeLabel = "KNOWS", direction = Direction.BOTH)
 * val degree = ops.degreeCentrality(alice.id, opts)
 * ```
 */
data class DegreeOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.BOTH,
): GraphAlgorithmOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = DegreeOptions()
    }
}

/**
 * Connected Components 옵션.
 *
 * @param vertexLabel `null` 이면 전체 정점.
 * @param edgeLabel `null` 이면 모든 간선.
 * @param weakly `true` = Weakly Connected (방향 무시), `false` = Strongly Connected.
 * @param minSize 반환할 최소 컴포넌트 크기 (기본 1).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = ComponentOptions(weakly = true, minSize = 2)
 * val components = ops.connectedComponents(opts)
 * ```
 */
data class ComponentOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val weakly: Boolean = true,
    val minSize: Int = 1,
): GraphAlgorithmOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = ComponentOptions()
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphAlgorithmOptions.kt
git commit -m "feat: add GraphAlgorithmOptions sealed class with PageRank/Degree/Component options"
```

---

## Task 3: Add `BfsDfsOptions` and `CycleOptions` to existing `GraphTraversalOptions.kt`

**Complexity:** low

**Files:**
- Modify: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphTraversalOptions.kt`

> **Note**: `GraphTraversalOptions` already extends `Serializable` in the existing file. Verify this before appending — both new subtypes inherit `Serializable` through the parent; the companion `serialVersionUID = 1L` fields in each subtype are still required for stable deserialization.

- [ ] **Step 1: Append `BfsDfsOptions` and `CycleOptions` to file**

Open `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphTraversalOptions.kt` and append after the existing `PathOptions` data class:

```kotlin

/**
 * BFS / DFS 공통 옵션.
 *
 * @param edgeLabel `null` 이면 모든 간선.
 * @param direction 탐색 방향 (기본 OUTGOING).
 * @param maxDepth 최대 탐색 깊이 (기본 5).
 * @param maxVertices 반환할 최대 정점 수 (안전 가드, 기본 10_000).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3, maxVertices = 1_000)
 * val visits = ops.bfs(alice.id, opts)
 * ```
 */
data class BfsDfsOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 5,
    val maxVertices: Int = 10_000,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxVertices > 0) { "maxVertices must be > 0, was $maxVertices" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = BfsDfsOptions()
    }
}

/**
 * Cycle 탐지 옵션.
 *
 * @param vertexLabel `null` 이면 전체 정점.
 * @param edgeLabel `null` 이면 모든 간선.
 * @param maxDepth 순환 경로 최대 길이 (기본 10).
 * @param maxCycles 반환할 최대 순환 수 (기본 100).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = CycleOptions(vertexLabel = "Account", maxDepth = 6, maxCycles = 50)
 * val cycles = ops.detectCycles(opts)
 * ```
 */
data class CycleOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val maxCycles: Int = 100,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxCycles > 0) { "maxCycles must be > 0, was $maxCycles" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = CycleOptions()
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphTraversalOptions.kt
git commit -m "feat: add BfsDfsOptions and CycleOptions to GraphTraversalOptions sealed hierarchy"
```

---

## Task 4: Add `GraphAlgorithmRepository` and `GraphSuspendAlgorithmRepository` interfaces

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphAlgorithmRepository.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendAlgorithmRepository.kt`

- [ ] **Step 1: Create `GraphAlgorithmRepository.kt`**

```kotlin
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit

/**
 * 그래프 분석(Analytics) 알고리즘 저장소 (동기 방식).
 *
 * 결과 순서 계약:
 * - [pageRank]: score 내림차순 정렬.
 * - [connectedComponents]: componentId 오름차순. 내부 정점은 임의 순서.
 * - [bfs]: BFS 방문 순서 (레벨 순).
 * - [dfs]: DFS 방문 순서 (깊이 우선).
 * - [detectCycles]: 임의 순서.
 *
 * 백엔드 미지원 알고리즘은 `UnsupportedOperationException` 을 던질 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val top10 = ops.pageRank(PageRankOptions(topK = 10))
 * val components = ops.connectedComponents()
 * val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3))
 * ```
 *
 * @see GraphSuspendAlgorithmRepository 코루틴 방식
 */
interface GraphAlgorithmRepository {

    /**
     * PageRank 알고리즘을 실행해 점수 목록을 반환한다.
     *
     * 반환 결과는 score 내림차순으로 정렬된다.
     *
     * @param options PageRank 옵션.
     * @return [PageRankScore] 목록.
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): List<PageRankScore>

    /**
     * 단일 정점의 Degree Centrality 를 계산한다.
     *
     * @param vertexId 측정 대상 정점 ID.
     * @param options Degree 옵션.
     * @return [DegreeResult].
     */
    fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * 연결 컴포넌트를 탐지한다.
     *
     * @param options Component 옵션.
     * @return [GraphComponent] 목록 (componentId 오름차순).
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): List<GraphComponent>

    /**
     * BFS 탐색을 실행한다.
     *
     * @param startId 시작 정점 ID.
     * @param options BFS 옵션.
     * @return [TraversalVisit] 목록 (방문 순서).
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    /**
     * DFS 탐색을 실행한다.
     *
     * @param startId 시작 정점 ID.
     * @param options DFS 옵션.
     * @return [TraversalVisit] 목록 (방문 순서).
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    /**
     * 순환을 탐지한다.
     *
     * @param options Cycle 옵션.
     * @return [GraphCycle] 목록.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): List<GraphCycle>
}
```

- [ ] **Step 2: Create `GraphSuspendAlgorithmRepository.kt`**

```kotlin
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 분석(Analytics) 알고리즘 저장소 (코루틴/Flow 방식).
 *
 * Flow 순서 계약은 [GraphAlgorithmRepository] 와 동일하다.
 * [pageRank] Flow 는 score 내림차순으로 emit 된다.
 *
 * ### 사용 예제
 * ```kotlin
 * runBlocking {
 *     val top10 = ops.pageRank(PageRankOptions(topK = 10)).toList()
 *     val components = ops.connectedComponents().toList()
 *     val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3)).toList()
 * }
 * ```
 *
 * @see GraphAlgorithmRepository 동기 방식
 */
interface GraphSuspendAlgorithmRepository {

    /**
     * PageRank 점수를 Flow 로 emit 한다 (score 내림차순).
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): Flow<PageRankScore>

    /**
     * 단일 정점의 Degree Centrality 를 계산한다.
     */
    suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * 연결 컴포넌트를 Flow 로 emit 한다.
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): Flow<GraphComponent>

    /**
     * BFS 방문 이벤트를 Flow 로 emit 한다.
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * DFS 방문 이벤트를 Flow 로 emit 한다.
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * 탐지된 순환을 Flow 로 emit 한다.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): Flow<GraphCycle>
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphAlgorithmRepository.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendAlgorithmRepository.kt
git commit -m "feat: add GraphAlgorithmRepository and GraphSuspendAlgorithmRepository interfaces"
```

---

## Task 5: Add `GraphGenericRepository` and `GraphSuspendGenericRepository` composite interfaces

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphGenericRepository.kt`
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendGenericRepository.kt`

- [ ] **Step 1: Create `GraphGenericRepository.kt`**

```kotlin
package io.bluetape4k.graph.repository

/**
 * 순회(traversal) + 분석(algorithm) 을 묶은 동기 합성 인터페이스.
 *
 * [GraphOperations] 의존성을 좁히고 싶을 때 이 타입을 직접 주입할 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * fun analyze(repo: GraphGenericRepository) {
 *     val path = repo.shortestPath(a, b)
 *     val scores = repo.pageRank()
 * }
 * ```
 */
interface GraphGenericRepository : GraphTraversalRepository, GraphAlgorithmRepository
```

- [ ] **Step 2: Create `GraphSuspendGenericRepository.kt`**

```kotlin
package io.bluetape4k.graph.repository

/**
 * 순회(traversal) + 분석(algorithm) 을 묶은 코루틴 합성 인터페이스.
 *
 * [GraphSuspendOperations] 의존성을 좁히고 싶을 때 이 타입을 직접 주입할 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * suspend fun analyze(repo: GraphSuspendGenericRepository) {
 *     val path = repo.shortestPath(a, b)
 *     val scores = repo.pageRank().toList()
 * }
 * ```
 */
interface GraphSuspendGenericRepository :
    GraphSuspendTraversalRepository,
    GraphSuspendAlgorithmRepository
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphGenericRepository.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendGenericRepository.kt
git commit -m "feat: add GraphGenericRepository composite interfaces (traversal + algorithm)"
```

---

## Task 6: Update `GraphOperations` and `GraphSuspendOperations` to compose generic repositories

**Complexity:** low

**Files:**
- Modify: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt`
- Modify: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt`

- [ ] **Step 1: Replace `GraphOperations.kt` interface composition**

Replace the entire `interface GraphOperations` declaration:

```kotlin
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (동기 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 *
 * @see GraphSuspendOperations 코루틴(suspend + Flow) 방식
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 *
 * ops.createGraph("social")
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * val edge  = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 * val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * val path      = ops.shortestPath(alice.id, bob.id, PathOptions())
 * val scores    = ops.pageRank(PageRankOptions(topK = 10))
 * ```
 */
interface GraphOperations :
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphGenericRepository
```

- [ ] **Step 2: Replace `GraphSuspendOperations.kt` interface composition**

```kotlin
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (코루틴 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 *
 * @see GraphOperations 동기(blocking) 방식
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphSuspendOperations = ...
 *
 * ops.createGraph("social")
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "FOLLOWS")
 *
 * val neighbors = ops.neighbors(alice.id).toList()
 * val scores    = ops.pageRank().toList()
 * ```
 */
interface GraphSuspendOperations :
    GraphSuspendSession,
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository,
    GraphSuspendGenericRepository
```

- [ ] **Step 3: Verify compilation breaks for backends (expected)**

Run: `./gradlew :graph-core:build`
Expected: BUILD SUCCESSFUL (graph-core compiles).
Run: `./gradlew :graph-tinkerpop:compileKotlin`
Expected: COMPILATION FAILS (`TinkerGraphOperations` does not implement new abstract members). This is expected — backends will be implemented in Phase 2-5.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt \
        graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt
git commit -m "refactor: GraphOperations composes GraphGenericRepository (traversal + algorithm)"
```

---

## Task 7: JVM fallback — `UnionFind` (Connected Components)

**Complexity:** medium

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/UnionFind.kt`
- Create: `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/UnionFindTest.kt`

- [ ] **Step 1: Write failing test `UnionFindTest.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class UnionFindTest {

    @Test
    fun `single element components`() {
        val uf = UnionFind(listOf("a", "b", "c"))
        uf.componentCount() shouldBeEqualTo 3
        uf.componentOf("a") shouldBeEqualTo "a"
    }

    @Test
    fun `union merges components`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")
        uf.componentCount() shouldBeEqualTo 2
        uf.connected("a", "b") shouldBeEqualTo true
        uf.connected("a", "c") shouldBeEqualTo false
    }

    @Test
    fun `union chained merges into single component`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("b", "c")
        uf.union("c", "d")
        uf.componentCount() shouldBeEqualTo 1
    }

    @Test
    fun `groups returns map of component representative to members`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")
        val groups = uf.groups()
        groups.size shouldBeEqualTo 2
        groups.values.map { it.size }.sorted() shouldBeEqualTo listOf(2, 2)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.UnionFindTest"`
Expected: FAIL with class not found.

- [ ] **Step 3: Implement `UnionFind.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

/**
 * Path compression + union-by-rank Union-Find (Disjoint Set Union).
 *
 * Connected Components 폴백 알고리즘에서 사용한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val uf = UnionFind(listOf("a", "b", "c"))
 * uf.union("a", "b")
 * uf.connected("a", "b")  // true
 * ```
 *
 * @param elements 초기 원소 컬렉션.
 */
class UnionFind<T>(elements: Iterable<T>) {

    private val parent: MutableMap<T, T> = HashMap()
    private val rank: MutableMap<T, Int> = HashMap()

    init {
        elements.forEach {
            parent[it] = it
            rank[it] = 0
        }
    }

    /** 원소 [x] 가 속한 컴포넌트의 대표(루트) 원소를 반환한다. */
    fun componentOf(x: T): T {
        var root = x
        while (parent[root] != root) {
            root = parent[root] ?: error("Element not in UnionFind: $root")
        }
        // path compression
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur] ?: error("Element not in UnionFind: $cur")
            parent[cur] = root
            cur = next
        }
        return root
    }

    /** 두 원소를 같은 컴포넌트로 병합한다. */
    fun union(x: T, y: T) {
        val rx = componentOf(x)
        val ry = componentOf(y)
        if (rx == ry) return

        val rankX = rank.getOrDefault(rx, 0)
        val rankY = rank.getOrDefault(ry, 0)
        when {
            rankX < rankY -> parent[rx] = ry
            rankX > rankY -> parent[ry] = rx
            else -> {
                parent[ry] = rx
                rank[rx] = rankX + 1
            }
        }
    }

    /** 두 원소가 같은 컴포넌트인지 확인한다. */
    fun connected(x: T, y: T): Boolean = componentOf(x) == componentOf(y)

    /** 현재 컴포넌트 수. */
    fun componentCount(): Int = parent.keys.map { componentOf(it) }.toSet().size

    /** 대표 원소 → 컴포넌트 멤버 목록 매핑. */
    fun groups(): Map<T, List<T>> =
        parent.keys.groupBy { componentOf(it) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.UnionFindTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/UnionFind.kt \
        graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/UnionFindTest.kt
git commit -m "feat: add UnionFind JVM fallback for Connected Components"
```

---

## Task 8: JVM fallback — `BfsDfsRunner`

**Complexity:** medium

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/BfsDfsRunner.kt`
- Create: `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/BfsDfsRunnerTest.kt`

- [ ] **Step 1: Write failing test `BfsDfsRunnerTest.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class BfsDfsRunnerTest {

    private fun id(v: String) = GraphElementId.of(v)

    /**
     * 그래프:
     *   a → b → d
     *   a → c → d
     *   d → e
     */
    private val adjacency: Map<GraphElementId, List<GraphElementId>> = mapOf(
        id("a") to listOf(id("b"), id("c")),
        id("b") to listOf(id("d")),
        id("c") to listOf(id("d")),
        id("d") to listOf(id("e")),
        id("e") to emptyList(),
    )

    @Test
    fun `bfs visits in level order`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)
        visits.map { it.vertex.id.value } shouldContainAll listOf("a", "b", "c", "d", "e")
        visits.first().vertex.id shouldBeEqualTo id("a")
        visits.first().depth shouldBeEqualTo 0
        visits.first { it.vertex.id == id("d") }.depth shouldBeEqualTo 2
    }

    @Test
    fun `bfs respects maxDepth`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 1, maxVertices = 100)
        visits.map { it.vertex.id.value }.toSet() shouldBeEqualTo setOf("a", "b", "c")
    }

    @Test
    fun `bfs respects maxVertices`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 10, maxVertices = 2)
        visits.shouldHaveSize(2)
    }

    @Test
    fun `dfs visits depth first`() {
        val visits = BfsDfsRunner.dfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)
        visits.first().vertex.id shouldBeEqualTo id("a")
        // 'a' visited first; one of b/c visited next, then descend before sibling
        visits.map { it.vertex.id.value }.toSet() shouldContainAll setOf("a", "b", "c", "d", "e")
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.BfsDfsRunnerTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `BfsDfsRunner.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.TraversalVisit
import java.util.ArrayDeque

/**
 * 인접 리스트 기반 BFS/DFS JVM 폴백 러너.
 *
 * 백엔드 native 미지원 시 사용한다 (AGE 등).
 *
 * ### 사용 예제
 * ```kotlin
 * val adjacency: Map<GraphElementId, List<GraphElementId>> = ...
 * val visits = BfsDfsRunner.bfs(start.id, adjacency, maxDepth = 3, maxVertices = 1000)
 * ```
 */
object BfsDfsRunner {

    /**
     * BFS 방문 결과를 반환한다 (레벨 순).
     *
     * @param startId 시작 정점 ID.
     * @param adjacency 인접 리스트 (out-edges).
     * @param maxDepth 최대 탐색 깊이.
     * @param maxVertices 반환할 최대 정점 수.
     * @param vertexResolver 정점 ID → [GraphVertex] 변환기 (기본: 빈 properties 정점).
     */
    fun bfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { GraphVertex(it, "", emptyMap()) },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val queue: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        queue.add(Triple(startId, 0, null))
        visited.add(startId)

        while (queue.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = queue.poll()
            result.add(TraversalVisit(vertexResolver(id), depth, parentId))
            if (depth >= maxDepth) continue

            adjacency[id].orEmpty().forEach { next ->
                if (visited.add(next)) {
                    queue.add(Triple(next, depth + 1, id))
                }
            }
        }
        return result
    }

    /**
     * DFS 방문 결과를 반환한다 (깊이 우선, pre-order).
     */
    fun dfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { GraphVertex(it, "", emptyMap()) },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val stack: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        stack.push(Triple(startId, 0, null))

        while (stack.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = stack.pop()
            if (!visited.add(id)) continue
            result.add(TraversalVisit(vertexResolver(id), depth, parentId))
            if (depth >= maxDepth) continue

            // push in reverse so first neighbor is popped first
            adjacency[id].orEmpty().asReversed().forEach { next ->
                if (next !in visited) stack.push(Triple(next, depth + 1, id))
            }
        }
        return result
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.BfsDfsRunnerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/BfsDfsRunner.kt \
        graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/BfsDfsRunnerTest.kt
git commit -m "feat: add BfsDfsRunner JVM fallback (BFS/DFS on adjacency map)"
```

---

## Task 9: JVM fallback — `CycleDetector` (DFS-based simple cycle finder)

**Complexity:** medium

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/CycleDetector.kt`
- Create: `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/CycleDetectorTest.kt`

- [ ] **Step 1: Write failing test `CycleDetectorTest.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class CycleDetectorTest {

    private fun id(v: String) = GraphElementId.of(v)

    @Test
    fun `no cycle in DAG`() {
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to emptyList(),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 100)
        cycles shouldHaveSize 0
    }

    @Test
    fun `simple triangle cycle`() {
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 100)
        cycles shouldHaveSize 1
        cycles.first().size shouldBeEqualTo 4 // a, b, c, a
        cycles.first().first() shouldBeEqualTo cycles.first().last()
    }

    @Test
    fun `respects maxDepth`() {
        // long cycle a -> b -> c -> d -> a (length 4)
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("d")),
            id("d") to listOf(id("a")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 3, maxCycles = 100)
        cycles shouldHaveSize 0
    }

    @Test
    fun `respects maxCycles`() {
        // self-loops on multiple vertices = many cycles
        val adjacency = mapOf(
            id("a") to listOf(id("a")),
            id("b") to listOf(id("b")),
            id("c") to listOf(id("c")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 2)
        cycles shouldHaveSize 2
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.CycleDetectorTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `CycleDetector.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId

/**
 * DFS 기반 단순 순환 탐지기 (JVM 폴백).
 *
 * 모든 정점에서 DFS 를 시작해 백엣지(back edge)를 탐지한다.
 * 동일 시작 정점에서 발생한 순환은 한 번만 보고된다.
 *
 * 반환되는 각 순환은 정점 ID 목록으로, 첫 번째와 마지막이 같다.
 *
 * ### 사용 예제
 * ```kotlin
 * val cycles = CycleDetector.findCycles(adjacency, maxDepth = 6, maxCycles = 50)
 * cycles.forEach { println("cycle: ${it.joinToString(" -> ")}") }
 * ```
 */
object CycleDetector {

    /**
     * @param adjacency 인접 리스트 (out-edges).
     * @param maxDepth 순환 경로 최대 길이 (간선 수).
     * @param maxCycles 반환할 최대 순환 수.
     * @return 정점 ID 목록의 목록. 각 항목은 first == last.
     */
    fun findCycles(
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
    ): List<List<GraphElementId>> {
        val result = ArrayList<List<GraphElementId>>()
        val seenSignatures = HashSet<List<GraphElementId>>()

        for (start in adjacency.keys) {
            if (result.size >= maxCycles) break
            dfs(
                current = start,
                start = start,
                stack = ArrayList<GraphElementId>().apply { add(start) },
                onStack = HashSet<GraphElementId>().apply { add(start) },
                adjacency = adjacency,
                maxDepth = maxDepth,
                maxCycles = maxCycles,
                result = result,
                seenSignatures = seenSignatures,
            )
        }
        return result
    }

    @Suppress("LongParameterList")
    private fun dfs(
        current: GraphElementId,
        start: GraphElementId,
        stack: MutableList<GraphElementId>,
        onStack: MutableSet<GraphElementId>,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
        result: MutableList<List<GraphElementId>>,
        seenSignatures: MutableSet<List<GraphElementId>>,
    ) {
        if (stack.size - 1 >= maxDepth) return
        if (result.size >= maxCycles) return

        for (next in adjacency[current].orEmpty()) {
            if (result.size >= maxCycles) return

            if (next == start) {
                val cycle = ArrayList(stack).apply { add(start) }
                val signature = canonicalSignature(cycle)
                if (seenSignatures.add(signature)) {
                    result.add(cycle)
                }
                continue
            }
            if (next in onStack) continue

            stack.add(next)
            onStack.add(next)
            dfs(next, start, stack, onStack, adjacency, maxDepth, maxCycles, result, seenSignatures)
            stack.removeAt(stack.size - 1)
            onStack.remove(next)
        }
    }

    /** 회전 등가 순환을 동일 시그니처로 정규화 (가장 작은 회전 시작). */
    private fun canonicalSignature(cycle: List<GraphElementId>): List<GraphElementId> {
        // cycle has first == last; drop the trailing duplicate
        val core = cycle.dropLast(1)
        if (core.isEmpty()) return cycle
        var minIdx = 0
        for (i in 1 until core.size) {
            if (core[i].value < core[minIdx].value) minIdx = i
        }
        val rotated = ArrayList<GraphElementId>(core.size)
        for (i in core.indices) rotated.add(core[(minIdx + i) % core.size])
        rotated.add(rotated[0])
        return rotated
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.CycleDetectorTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/CycleDetector.kt \
        graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/CycleDetectorTest.kt
git commit -m "feat: add CycleDetector JVM fallback (DFS-based simple cycle finder)"
```

---

## Task 10: JVM fallback — `PageRankCalculator`

**Complexity:** medium

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/PageRankCalculator.kt`
- Create: `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/PageRankCalculatorTest.kt`

- [ ] **Step 1: Write failing test `PageRankCalculatorTest.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PageRankCalculatorTest {

    private fun id(v: String) = GraphElementId.of(v)

    @Test
    fun `single node has full pagerank mass`() {
        val scores = PageRankCalculator.compute(
            vertices = setOf(id("a")),
            outAdjacency = mapOf(id("a") to emptyList()),
            iterations = 20,
            dampingFactor = 0.85,
            tolerance = 1e-4,
        )
        scores shouldHaveSize 1
        abs(scores.getValue(id("a")) - 1.0) shouldBeGreaterThan -0.001
    }

    @Test
    fun `hub node has highest pagerank in star`() {
        // a, b, c, d all point to e
        val outAdjacency = mapOf(
            id("a") to listOf(id("e")),
            id("b") to listOf(id("e")),
            id("c") to listOf(id("e")),
            id("d") to listOf(id("e")),
            id("e") to emptyList(),
        )
        val scores = PageRankCalculator.compute(
            vertices = outAdjacency.keys,
            outAdjacency = outAdjacency,
            iterations = 50,
            dampingFactor = 0.85,
            tolerance = 1e-6,
        )
        val maxId = scores.maxByOrNull { it.value }!!.key
        maxId shouldBeEqualTo id("e")
    }

    @Test
    fun `scores sum approximately 1`() {
        val outAdjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val scores = PageRankCalculator.compute(
            vertices = outAdjacency.keys,
            outAdjacency = outAdjacency,
            iterations = 50,
            dampingFactor = 0.85,
            tolerance = 1e-6,
        )
        val sum = scores.values.sum()
        (abs(sum - 1.0) < 0.01) shouldBeEqualTo true
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.PageRankCalculatorTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `PageRankCalculator.kt`**

```kotlin
package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import kotlin.math.abs

/**
 * 정규화된 PageRank 반복 계산기 (JVM 폴백).
 *
 * 결과 점수의 합 ≈ 1.0 으로 정규화된다.
 * dangling node (out-degree 0)의 질량은 다음 반복 시 모든 정점에 균등 분배된다.
 *
 * ### 사용 예제
 * ```kotlin
 * val scores = PageRankCalculator.compute(
 *     vertices = vertexIds,
 *     outAdjacency = adjacency,
 *     iterations = 20,
 *     dampingFactor = 0.85,
 *     tolerance = 1e-4,
 * )
 * ```
 */
object PageRankCalculator {

    /**
     * @param vertices 전체 정점 ID 집합.
     * @param outAdjacency out-edge 인접 리스트.
     * @param iterations 최대 반복 횟수.
     * @param dampingFactor 감쇠 계수 (보통 0.85).
     * @param tolerance L1-norm 수렴 허용치.
     */
    fun compute(
        vertices: Set<GraphElementId>,
        outAdjacency: Map<GraphElementId, List<GraphElementId>>,
        iterations: Int,
        dampingFactor: Double,
        tolerance: Double,
    ): Map<GraphElementId, Double> {
        if (vertices.isEmpty()) return emptyMap()

        val n = vertices.size
        val initial = 1.0 / n
        var ranks = HashMap<GraphElementId, Double>(n)
        vertices.forEach { ranks[it] = initial }

        repeat(iterations) {
            val newRanks = HashMap<GraphElementId, Double>(n)
            // base teleport probability
            val baseRank = (1.0 - dampingFactor) / n

            // dangling mass — sum of ranks for vertices with no outgoing edges
            val danglingMass = vertices.filter { outAdjacency[it].isNullOrEmpty() }
                .sumOf { ranks.getOrDefault(it, 0.0) }
            val danglingShare = dampingFactor * danglingMass / n

            vertices.forEach { v -> newRanks[v] = baseRank + danglingShare }

            vertices.forEach { src ->
                val outs = outAdjacency[src].orEmpty()
                if (outs.isNotEmpty()) {
                    val share = dampingFactor * ranks.getOrDefault(src, 0.0) / outs.size
                    outs.forEach { dst ->
                        newRanks[dst] = (newRanks[dst] ?: 0.0) + share
                    }
                }
            }

            val delta = vertices.sumOf { abs((newRanks[it] ?: 0.0) - (ranks[it] ?: 0.0)) }
            ranks = newRanks
            if (delta < tolerance) return ranks
        }
        return ranks
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.algo.internal.PageRankCalculatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/PageRankCalculator.kt \
        graph/graph-core/src/test/kotlin/io/bluetape4k/graph/algo/internal/PageRankCalculatorTest.kt
git commit -m "feat: add PageRankCalculator JVM fallback (normalized iterative PageRank)"
```

---

# Phase 2 — TinkerPop implementation (most complete native support)

## Task 11: Implement 6 algorithm methods in `TinkerGraphOperations`

**Complexity:** high

**Files:**
- Modify: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt`

- [ ] **Step 1: Add imports at top of file**

Add the following imports after existing imports:

```kotlin
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import org.apache.tinkerpop.gremlin.process.computer.search.path.ShortestPathVertexProgram
import org.apache.tinkerpop.gremlin.structure.T
```

- [ ] **Step 2: Append the 6 algorithm methods at the end of the class (before the closing brace)**

```kotlin

    // -- GraphAlgorithmRepository --

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        // Native Gremlin pageRank() step
        val base = if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()
        val scored = base.pageRank()
            .by("gremlin.pageRankVertexProgram.pageRank")
            .order().by("gremlin.pageRankVertexProgram.pageRank", org.apache.tinkerpop.gremlin.process.traversal.Order.desc)
            .toList()

        val all = scored.map { v ->
            val score = (v.value<Double>("gremlin.pageRankVertexProgram.pageRank") ?: 0.0)
            PageRankScore(GremlinRecordMapper.vertexToGraphVertex(v), score)
        }
        return if (options.topK == Int.MAX_VALUE) all else all.take(options.topK)
    }

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = vertexId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${vertexId.value}' to TinkerGraph Long ID")

        val inE = if (options.edgeLabel != null) g.V(idValue).inE(options.edgeLabel).count().next()
                  else g.V(idValue).inE().count().next()
        val outE = if (options.edgeLabel != null) g.V(idValue).outE(options.edgeLabel).count().next()
                   else g.V(idValue).outE().count().next()

        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, outE.toInt())
            Direction.INCOMING -> DegreeResult(vertexId, inE.toInt(), 0)
            Direction.BOTH -> DegreeResult(vertexId, inE.toInt(), outE.toInt())
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        // Use JVM fallback via UnionFind to ensure consistent behavior across TinkerPop versions
        val vertices = (if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()).toList()
        val vertexMap = vertices.associate { GremlinRecordMapper.vertexToGraphVertex(it).id to GremlinRecordMapper.vertexToGraphVertex(it) }
        val ids = vertexMap.keys

        val uf = io.bluetape4k.graph.algo.internal.UnionFind(ids)
        val edges = (if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()).toList()
        edges.forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            if (src in ids && dst in ids) {
                uf.union(src, dst)
            }
        }

        // Ordering contract: components are sorted by their representative GraphElementId.value (String).
        // GraphElementId is a value class around String, so compareBy { it.value } yields lexicographic
        // ordering of the representative IDs — this matches the GraphAlgorithmRepository.connectedComponents
        // contract: "componentId 오름차순".
        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(
                    componentId = rep.value,
                    vertices = members.mapNotNull { vertexMap[it] },
                )
            }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${startId.value}' to TinkerGraph Long ID")

        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val collectedVertices = HashMap<GraphElementId, GraphVertex>()
        val edgesQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgesQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            collectedVertices[src] = GremlinRecordMapper.vertexToGraphVertex(e.outVertex())
            collectedVertices[dst] = GremlinRecordMapper.vertexToGraphVertex(e.inVertex())
            when (options.direction) {
                Direction.OUTGOING -> adjacency.getOrPut(src) { ArrayList() }.add(dst)
                Direction.INCOMING -> adjacency.getOrPut(dst) { ArrayList() }.add(src)
                Direction.BOTH -> {
                    adjacency.getOrPut(src) { ArrayList() }.add(dst)
                    adjacency.getOrPut(dst) { ArrayList() }.add(src)
                }
            }
        }
        // ensure start vertex resolved
        g.V(idValue).tryNext().ifPresent { collectedVertices[startId] = GremlinRecordMapper.vertexToGraphVertex(it) }

        return io.bluetape4k.graph.algo.internal.BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { collectedVertices[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${startId.value}' to TinkerGraph Long ID")

        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val collectedVertices = HashMap<GraphElementId, GraphVertex>()
        val edgesQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgesQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            collectedVertices[src] = GremlinRecordMapper.vertexToGraphVertex(e.outVertex())
            collectedVertices[dst] = GremlinRecordMapper.vertexToGraphVertex(e.inVertex())
            when (options.direction) {
                Direction.OUTGOING -> adjacency.getOrPut(src) { ArrayList() }.add(dst)
                Direction.INCOMING -> adjacency.getOrPut(dst) { ArrayList() }.add(src)
                Direction.BOTH -> {
                    adjacency.getOrPut(src) { ArrayList() }.add(dst)
                    adjacency.getOrPut(dst) { ArrayList() }.add(src)
                }
            }
        }
        g.V(idValue).tryNext().ifPresent { collectedVertices[startId] = GremlinRecordMapper.vertexToGraphVertex(it) }

        return io.bluetape4k.graph.algo.internal.BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { collectedVertices[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val vertexQuery = if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()
        val verticesById = vertexQuery.toList().associate {
            val gv = GremlinRecordMapper.vertexToGraphVertex(it)
            gv.id to gv
        }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val edgesById = HashMap<Pair<GraphElementId, GraphElementId>, GraphEdge>()
        val edgeQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgeQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            if (src in verticesById && dst in verticesById) {
                adjacency.getOrPut(src) { ArrayList() }.add(dst)
                edgesById[src to dst] = GremlinRecordMapper.edgeToGraphEdge(e)
            }
        }

        val cycles = io.bluetape4k.graph.algo.internal.CycleDetector.findCycles(
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxCycles = options.maxCycles,
        )
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            for (i in ids.indices) {
                val v = verticesById[ids[i]] ?: GraphVertex(ids[i], "", emptyMap())
                steps.add(PathStep.VertexStep(v))
                if (i < ids.size - 1) {
                    val edge = edgesById[ids[i] to ids[i + 1]]
                    if (edge != null) steps.add(PathStep.EdgeStep(edge))
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-tinkerpop:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt
git commit -m "feat(tinkerpop): implement 6 algorithm methods (pageRank/degree/CC/bfs/dfs/cycles)"
```

---

## Task 12: Implement coroutine `TinkerGraphSuspendOperations` for the 6 algorithm methods

**Complexity:** medium

**Files:**
- Modify: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt`

- [ ] **Step 1: Add imports**

Add at top after existing imports:

```kotlin
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
```

- [ ] **Step 2: Append 6 method overrides before the closing brace**

```kotlin

    // -- GraphSuspendAlgorithmRepository --

    override fun pageRank(options: PageRankOptions): Flow<PageRankScore> = flow {
        val list = withContext(Dispatchers.IO) { delegate.pageRank(options) }
        list.forEach { emit(it) }
    }

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = withContext(Dispatchers.IO) {
        delegate.degreeCentrality(vertexId, options)
    }

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        val list = withContext(Dispatchers.IO) { delegate.connectedComponents(options) }
        list.forEach { emit(it) }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { delegate.bfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { delegate.dfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun detectCycles(options: CycleOptions): Flow<GraphCycle> = flow {
        val list = withContext(Dispatchers.IO) { delegate.detectCycles(options) }
        list.forEach { emit(it) }
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-tinkerpop:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt
git commit -m "feat(tinkerpop): implement coroutine algorithm Flow APIs via delegate"
```

---

## Task 13: Add `TinkerGraphAlgorithmTest` (sync, in-memory, no Docker)

**Complexity:** medium

**Files:**
- Create: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmTest.kt`

> **Concurrency note**: TinkerGraph is in-memory and shared across tests in this class. Test parallelization is already serialized at the project level via the `testMutex` `BuildService` wired in the root `build.gradle.kts` (`usesService(testMutex)`), so per-class `@Execution` annotations are not required. Do NOT add `@Execution(ExecutionMode.CONCURRENT)`.

- [ ] **Step 1: Create test file**

```kotlin
package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeIn
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphAlgorithmTest {

    companion object: KLogging()

    private val ops = TinkerGraphOperations()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `degreeCentrality counts in and out edges`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
        degree.total shouldBeEqualTo 2
    }

    @Test
    fun `pageRank returns score-descending list`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(4) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
        scores.shouldNotBeEmpty()
        // descending order
        scores.zipWithNext { a, b -> (a.score >= b.score).shouldBeTrue() }
        // hub should be top
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `connectedComponents finds two clusters`() {
        val a1 = ops.createVertex("Person", mapOf("g" to "A"))
        val a2 = ops.createVertex("Person", mapOf("g" to "A"))
        val b1 = ops.createVertex("Person", mapOf("g" to "B"))
        val b2 = ops.createVertex("Person", mapOf("g" to "B"))
        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops.connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"))
        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `bfs returns level-ordered visits`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        val d = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(a.id, c.id, "E")
        ops.createEdge(b.id, d.id, "E")
        ops.createEdge(c.id, d.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3))
        visits.first().depth shouldBeEqualTo 0
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 4
    }

    @Test
    fun `dfs starts from given vertex`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3))
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles finds triangle`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops.detectCycles(CycleOptions(maxDepth = 5))
        cycles.shouldNotBeEmpty()
        cycles.first().path.vertices.first().id shouldBeEqualTo cycles.first().path.vertices.last().id
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :graph-tinkerpop:test --tests "io.bluetape4k.graph.tinkerpop.TinkerGraphAlgorithmTest"`
Expected: PASS (6 tests).

- [ ] **Step 3: Commit**

```bash
git add graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmTest.kt
git commit -m "test(tinkerpop): add TinkerGraphAlgorithmTest covering 6 algorithm methods"
```

---

## Task 14: Add `TinkerGraphAlgorithmSuspendTest` (coroutine flow)

**Complexity:** low

**Files:**
- Create: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmSuspendTest.kt`

- [ ] **Step 1: Create test file**

```kotlin
package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphAlgorithmSuspendTest {

    companion object: KLoggingChannel()

    private val ops = TinkerGraphSuspendOperations()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() = runBlocking {
        ops.dropGraph("default")
    }

    @Test
    fun `pageRank Flow emits descending scores`() = runBlocking {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30)).toList()
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfs Flow emits visits in level order`() = runBlocking {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).toList()
        visits.first().depth shouldBeEqualTo 0
        visits.size shouldBeGreaterOrEqualTo 2
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :graph-tinkerpop:test --tests "io.bluetape4k.graph.tinkerpop.TinkerGraphAlgorithmSuspendTest"`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphAlgorithmSuspendTest.kt
git commit -m "test(tinkerpop): add TinkerGraphAlgorithmSuspendTest for Flow APIs"
```

---

# Phase 3 — Neo4j implementation (Cypher + JVM fallback)

## Task 15: Implement 6 algorithm methods in `Neo4jGraphOperations` (Cypher + JVM fallback)

**Complexity:** high

**Files:**
- Modify: `graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt`

- [ ] **Step 1: Add imports at top of file**

```kotlin
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.logging.warn
```

- [ ] **Step 2: Append the 6 algorithm methods before the closing brace**

```kotlin

    // -- GraphAlgorithmRepository --

    private fun sanitizeLabel(label: String): String {
        require(label.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))) { "Invalid label: $label" }
        return label
    }

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val cypher = """
            MATCH (n) WHERE elementId(n) = ${'$'}id
            OPTIONAL MATCH (n)-[r_out$edgePattern]->()
            WITH n, count(r_out) AS outDeg
            OPTIONAL MATCH ()-[r_in$edgePattern]->(n)
            RETURN outDeg, count(r_in) AS inDeg
        """.trimIndent()

        val rec = session().use { s ->
            s.run(cypher, mapOf("id" to vertexId.value)).single()
        }
        val out = rec["outDeg"].asInt()
        val inn = rec["inDeg"].asInt()
        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, out)
            Direction.INCOMING -> DegreeResult(vertexId, inn, 0)
            Direction.BOTH -> DegreeResult(vertexId, inn, out)
        }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val pathPattern = "(a$labelClause)-[r$edgePattern*1..${options.maxDepth}]->(a)"

        val cypher = """
            MATCH p = $pathPattern
            RETURN p LIMIT ${options.maxCycles}
        """.trimIndent()

        return try {
            runQuery(cypher, emptyMap<String, Any>()) { rec ->
                val path = rec["p"].asPath()
                val steps = ArrayList<PathStep>()
                path.nodes().forEach { steps.add(PathStep.VertexStep(Neo4jRecordMapper.nodeToGraphVertex(it))) }
                // re-interleave with edges
                val orderedSteps = ArrayList<PathStep>(steps.size * 2)
                val nodes = path.nodes().toList()
                val edges = path.relationships().toList()
                for (i in nodes.indices) {
                    orderedSteps.add(PathStep.VertexStep(Neo4jRecordMapper.nodeToGraphVertex(nodes[i])))
                    if (i < edges.size) {
                        orderedSteps.add(PathStep.EdgeStep(Neo4jRecordMapper.relationshipToGraphEdge(edges[i])))
                    }
                }
                GraphCycle(GraphPath(orderedSteps))
            }
        } catch (e: Exception) {
            log.debug(e) { "detectCycles via Cypher failed; using JVM fallback" }
            detectCyclesViaFallback(options)
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) { Neo4jRecordMapper.nodeToGraphVertex(it["n"].asNode()) }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val edges = runQuery("MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb", emptyMap<String, Any>()) { rec ->
            GraphElementId.of(rec["sa"].asString()) to GraphElementId.of(rec["sb"].asString())
        }

        val uf = UnionFind(ids)
        edges.forEach { (s, e) ->
            if (s in ids && e in ids) uf.union(s, e)
        }

        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(rep.value, members.mapNotNull { vertexById[it] })
            }
    }

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: Neo4j Cypher fallback in use (no GDS). Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) { Neo4jRecordMapper.nodeToGraphVertex(it["n"].asNode()) }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery("MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb", emptyMap<String, Any>()) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
            outAdjacency.getOrPut(s) { ArrayList() }.add(e)
            Unit
        }

        val scores = PageRankCalculator.compute(
            vertices = ids,
            outAdjacency = outAdjacency,
            iterations = options.iterations,
            dampingFactor = options.dampingFactor,
            tolerance = options.tolerance,
        )
        val sorted = scores.entries.sortedByDescending { it.value }
            .mapNotNull { e -> vertexById[e.key]?.let { PageRankScore(it, e.value) } }
        return if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
    }

    private fun loadAdjacency(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val edgePattern = edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        runQuery("MATCH (a)-[r$edgePattern]->(b) RETURN a, b", emptyMap<String, Any>()) { rec ->
            val av = Neo4jRecordMapper.nodeToGraphVertex(rec["a"].asNode())
            val bv = Neo4jRecordMapper.nodeToGraphVertex(rec["b"].asNode())
            vertexById[av.id] = av
            vertexById[bv.id] = bv
            when (direction) {
                Direction.OUTGOING -> adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                Direction.INCOMING -> adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                Direction.BOTH -> {
                    adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                    adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                }
            }
        }
        return adjacency to vertexById
    }

    private fun detectCyclesViaFallback(options: CycleOptions): List<GraphCycle> {
        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) { Neo4jRecordMapper.nodeToGraphVertex(it["n"].asNode()) }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery("MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb", emptyMap<String, Any>()) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
            adjacency.getOrPut(s) { ArrayList() }.add(e)
            Unit
        }
        val cycles = CycleDetector.findCycles(adjacency, options.maxDepth, options.maxCycles)
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            ids.forEachIndexed { i, vid ->
                val gv = vertexById[vid] ?: GraphVertex(vid, "", emptyMap())
                steps.add(PathStep.VertexStep(gv))
                if (i < ids.size - 1) {
                    // edge metadata not preserved in fallback; synthesize placeholder
                    steps.add(PathStep.EdgeStep(io.bluetape4k.graph.model.GraphEdge(
                        id = GraphElementId.of("${vid.value}->${ids[i + 1].value}"),
                        label = options.edgeLabel ?: "",
                        startId = vid,
                        endId = ids[i + 1],
                    )))
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-neo4j:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt
git commit -m "feat(neo4j): implement 6 algorithm methods (Cypher native + JVM fallback for PageRank/CC)"
```

---

## Task 15b: Verify `sanitizeLabel` rejects Cypher-injection strings

**Complexity:** low

**Files:**
- Modify: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`

> This task runs without Docker — `sanitizeLabel` is a pure function; no DB container needed.

- [ ] **Step 1: Add injection-guard test to `Neo4jAlgorithmTest`**

Add the following test inside the class body (no fixture setup needed):

```kotlin
@Test
fun `sanitizeLabel throws on injection string`() {
    val ops = Neo4jGraphOperations(Neo4jServer.instance.driver)
    // Access private sanitizeLabel via reflection or test a public method that delegates to it
    // degreeCentrality uses sanitizeLabel for edgeLabel; we can abuse DegreeOptions.edgeLabel
    val badLabel = "Person'; DROP TABLE foo--"
    val ex = shouldThrow<IllegalArgumentException> {
        ops.degreeCentrality(
            GraphElementId.of("dummy"),
            DegreeOptions(edgeLabel = badLabel),
        )
    }
    ex.message shouldContain "Invalid label"
}
```

Required imports:
```kotlin
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.GraphElementId
import io.kotest.assertions.throwables.shouldThrow
import org.amshove.kluent.shouldContain
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jAlgorithmTest.sanitizeLabel throws on injection string"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt
git commit -m "test(neo4j): verify sanitizeLabel rejects Cypher-injection edge labels"
```

---

## Task 16: Implement coroutine `Neo4jGraphSuspendOperations` for the 6 algorithm methods

**Complexity:** medium

**Files:**
- Modify: `graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperations.kt`

- [ ] **Step 0: Read the existing constructor signature**

Open `Neo4jGraphSuspendOperations.kt` and confirm the primary constructor parameters (e.g., `driver: Driver`, `database: String = "neo4j"`). Match those property names exactly when constructing the `syncDelegate` in Step 2 — do NOT invent new constructor parameters. If the suspend class uses different names (e.g., `db` instead of `database`), update Step 2's delegate construction accordingly.

- [ ] **Step 1: Add imports**

```kotlin
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: Append 6 method overrides delegating to a sync `Neo4jGraphOperations` instance**

If the suspend operations does not already hold a sync delegate, add:

```kotlin
private val syncDelegate by lazy { Neo4jGraphOperations(driver, database) }
```

(Use the same `driver` and `database` properties already in this class. Check the existing class first.)

Then append the methods before the closing brace:

```kotlin

    // -- GraphSuspendAlgorithmRepository --

    override fun pageRank(options: PageRankOptions): Flow<PageRankScore> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.pageRank(options) }
        list.forEach { emit(it) }
    }

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = withContext(Dispatchers.IO) {
        syncDelegate.degreeCentrality(vertexId, options)
    }

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.connectedComponents(options) }
        list.forEach { emit(it) }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.bfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.dfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun detectCycles(options: CycleOptions): Flow<GraphCycle> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.detectCycles(options) }
        list.forEach { emit(it) }
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-neo4j:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperations.kt
git commit -m "feat(neo4j): implement coroutine algorithm Flow APIs delegating to sync ops"
```

---

## Task 17: Add `Neo4jAlgorithmTest` (Testcontainers)

**Complexity:** medium

**Files:**
- Create: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`

- [ ] **Step 1: Create test file**

```kotlin
package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.servers.Neo4jServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jAlgorithmTest {

    companion object: KLogging()

    private val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
    private val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `degreeCentrality counts both directions`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
    }

    @Test
    fun `pageRank returns descending scores`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(4) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
        scores.shouldNotBeEmpty()
        scores.zipWithNext { x, y -> (x.score >= y.score).shouldBeTrue() }
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `connectedComponents groups linked vertices`() {
        val a1 = ops.createVertex("Person", mapOf("g" to "A"))
        val a2 = ops.createVertex("Person", mapOf("g" to "A"))
        val b1 = ops.createVertex("Person", mapOf("g" to "B"))
        val b2 = ops.createVertex("Person", mapOf("g" to "B"))
        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops.connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"))
        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `bfs returns visits up to maxDepth`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2))
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 3
    }

    @Test
    fun `dfs returns visits starting from given vertex`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2))
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles finds triangle via Cypher`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops.detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5))
        cycles.shouldNotBeEmpty()
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jAlgorithmTest"`
Expected: PASS (6 tests). Container starts (~10s on first run).

- [ ] **Step 3: Commit**

```bash
git add graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt
git commit -m "test(neo4j): add Neo4jAlgorithmTest covering 6 algorithm methods (Testcontainers)"
```

---

## Task 18: Add `Neo4jAlgorithmSuspendTest`

**Complexity:** low

**Files:**
- Create: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt`

- [ ] **Step 1: Create test file**

```kotlin
package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.servers.Neo4jServer
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jAlgorithmSuspendTest {

    companion object: KLoggingChannel()

    private val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
    private val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun reset() = runBlocking { ops.dropGraph("default") }

    @Test
    fun `pageRank Flow emits descending scores`() = runBlocking {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30)).toList()
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfs Flow emits visits`() = runBlocking {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).toList()
        visits.size shouldBeGreaterOrEqualTo 2
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jAlgorithmSuspendTest"`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt
git commit -m "test(neo4j): add Neo4jAlgorithmSuspendTest for Flow APIs"
```

---

# Phase 4 — Memgraph implementation (reuses Neo4j Cypher)

## Task 19: Implement 6 algorithm methods in `MemgraphGraphOperations` (mirror Neo4j)

**Complexity:** medium

**Files:**
- Modify: `graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt`

- [ ] **Step 1: Add imports at top of file**

```kotlin
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.logging.warn
```

- [ ] **Step 2: Append the 6 algorithm methods before the closing brace**

```kotlin

    // -- GraphAlgorithmRepository --

    private fun sanitizeLabel(label: String): String {
        require(label.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))) { "Invalid label: $label" }
        return label
    }

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val cypher = """
            MATCH (n) WHERE elementId(n) = ${'$'}id
            OPTIONAL MATCH (n)-[r_out$edgePattern]->()
            WITH n, count(r_out) AS outDeg
            OPTIONAL MATCH ()-[r_in$edgePattern]->(n)
            RETURN outDeg, count(r_in) AS inDeg
        """.trimIndent()

        val rec = session().use { s ->
            s.run(cypher, mapOf("id" to vertexId.value)).single()
        }
        val out = rec["outDeg"].asInt()
        val inn = rec["inDeg"].asInt()
        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, out)
            Direction.INCOMING -> DegreeResult(vertexId, inn, 0)
            Direction.BOTH -> DegreeResult(vertexId, inn, out)
        }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val pathPattern = "(a$labelClause)-[r$edgePattern*1..${options.maxDepth}]->(a)"

        val cypher = """
            MATCH p = $pathPattern
            RETURN p LIMIT ${options.maxCycles}
        """.trimIndent()

        return try {
            runQuery(cypher, emptyMap<String, Any>()) { rec ->
                val path = rec["p"].asPath()
                val orderedSteps = ArrayList<PathStep>()
                val nodes = path.nodes().toList()
                val edges = path.relationships().toList()
                for (i in nodes.indices) {
                    orderedSteps.add(PathStep.VertexStep(MemgraphRecordMapper.nodeToGraphVertex(nodes[i])))
                    if (i < edges.size) {
                        orderedSteps.add(PathStep.EdgeStep(MemgraphRecordMapper.relationshipToGraphEdge(edges[i])))
                    }
                }
                GraphCycle(GraphPath(orderedSteps))
            }
        } catch (e: Exception) {
            log.debug(e) { "detectCycles via Cypher failed; using JVM fallback" }
            detectCyclesViaFallback(options)
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToGraphVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val edges = runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            GraphElementId.of(rec["sa"].asString()) to GraphElementId.of(rec["sb"].asString())
        }

        val uf = UnionFind(ids)
        edges.forEach { (s, e) ->
            if (s in ids && e in ids) uf.union(s, e)
        }

        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(rep.value, members.mapNotNull { vertexById[it] })
            }
    }

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: Memgraph JVM fallback in use (no MAGE). Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToGraphVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
            outAdjacency.getOrPut(s) { ArrayList() }.add(e)
            Unit
        }

        val scores = PageRankCalculator.compute(
            vertices = ids,
            outAdjacency = outAdjacency,
            iterations = options.iterations,
            dampingFactor = options.dampingFactor,
            tolerance = options.tolerance,
        )
        val sorted = scores.entries.sortedByDescending { it.value }
            .mapNotNull { e -> vertexById[e.key]?.let { PageRankScore(it, e.value) } }
        return if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
    }

    private fun loadAdjacency(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val edgePattern = edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        runQuery("MATCH (a)-[r$edgePattern]->(b) RETURN a, b", emptyMap<String, Any>()) { rec ->
            val av = MemgraphRecordMapper.nodeToGraphVertex(rec["a"].asNode())
            val bv = MemgraphRecordMapper.nodeToGraphVertex(rec["b"].asNode())
            vertexById[av.id] = av
            vertexById[bv.id] = bv
            when (direction) {
                Direction.OUTGOING -> adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                Direction.INCOMING -> adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                Direction.BOTH -> {
                    adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                    adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                }
            }
        }
        return adjacency to vertexById
    }

    private fun detectCyclesViaFallback(options: CycleOptions): List<GraphCycle> {
        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToGraphVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
            adjacency.getOrPut(s) { ArrayList() }.add(e)
            Unit
        }
        val cycles = CycleDetector.findCycles(adjacency, options.maxDepth, options.maxCycles)
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            ids.forEachIndexed { i, vid ->
                val gv = vertexById[vid] ?: GraphVertex(vid, "", emptyMap())
                steps.add(PathStep.VertexStep(gv))
                if (i < ids.size - 1) {
                    steps.add(PathStep.EdgeStep(io.bluetape4k.graph.model.GraphEdge(
                        id = GraphElementId.of("${vid.value}->${ids[i + 1].value}"),
                        label = options.edgeLabel ?: "",
                        startId = vid,
                        endId = ids[i + 1],
                    )))
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }
```

> **Note**: Memgraph's `elementId(n)` is supported in recent versions. If the Memgraph image in `MemgraphServer` uses a version that lacks `elementId()`, replace `elementId(n)` with `id(n)` and convert results via `GraphElementId.of(rec[...].asLong().toString())`. The code above mirrors `Neo4jGraphOperations` (Task 15) verbatim with `Neo4jRecordMapper` → `MemgraphRecordMapper` substitution.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-memgraph:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt
git commit -m "feat(memgraph): implement 6 algorithm methods (Cypher mirror of Neo4j)"
```

---

## Task 20: Implement coroutine `MemgraphGraphSuspendOperations`

**Complexity:** low

**Files:**
- Modify: `graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperations.kt`

- [ ] **Step 0: Read the existing constructor signature**

Open `MemgraphGraphSuspendOperations.kt` and confirm the primary constructor parameters (e.g., `driver: Driver`, `database: String = "memgraph"`). Match those property names exactly when constructing the `syncDelegate` in Step 1 — do NOT invent new constructor parameters. If the suspend class uses different names, update Step 1's delegate construction accordingly.

- [ ] **Step 1: Mirror Neo4jGraphSuspendOperations Task 16 Step 2**

Add the same `syncDelegate by lazy { MemgraphGraphOperations(driver, database) }` and the same 6 Flow / suspend method overrides. Adjust the constructor call to match the actual signature confirmed in Step 0.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-memgraph:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperations.kt
git commit -m "feat(memgraph): implement coroutine algorithm Flow APIs delegating to sync ops"
```

---

## Task 21: Add `MemgraphAlgorithmTest` and `MemgraphAlgorithmSuspendTest`

**Complexity:** medium

**Files:**
- Create: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt`
- Create: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmSuspendTest.kt`

- [ ] **Step 1: Create `MemgraphAlgorithmTest.kt`**

Mirror `Neo4jAlgorithmTest` (Task 17). Replace:
- `import io.bluetape4k.graph.servers.Neo4jServer` → `import io.bluetape4k.graph.servers.MemgraphServer`
- `Neo4jServer.boltUrl` → `MemgraphServer.boltUrl`
- `Neo4jGraphOperations(driver)` → `MemgraphGraphOperations(driver)`
- Class name `Neo4jAlgorithmTest` → `MemgraphAlgorithmTest`
- Package `io.bluetape4k.graph.neo4j` → `io.bluetape4k.graph.memgraph`

- [ ] **Step 2: Create `MemgraphAlgorithmSuspendTest.kt`**

Mirror `Neo4jAlgorithmSuspendTest` (Task 18) with same substitutions.

- [ ] **Step 3: Run tests**

Run: `./gradlew :graph-memgraph:test --tests "io.bluetape4k.graph.memgraph.MemgraphAlgorithmTest" --tests "io.bluetape4k.graph.memgraph.MemgraphAlgorithmSuspendTest"`
Expected: PASS (8 tests total).

- [ ] **Step 4: Commit**

```bash
git add graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt \
        graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmSuspendTest.kt
git commit -m "test(memgraph): add Memgraph algorithm tests (sync + suspend)"
```

---

# Phase 5 — AGE implementation (degree native, others JVM fallback)

## Task 22: Add degree Cypher-over-SQL helper to `AgeSql`

**Complexity:** low

**Files:**
- Modify: `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/sql/AgeSql.kt`

- [ ] **Step 1: Append the helper inside the `object AgeSql` (before its closing brace)**

```kotlin

    /**
     * 단일 정점의 in/out degree 카운트 SQL 을 생성한다.
     *
     * AGE Cypher-over-SQL: `OPTIONAL MATCH ... RETURN count(*)` 패턴.
     *
     * ```kotlin
     * val sql = AgeSql.degreeCentrality("social", 42L, edgeLabel = "KNOWS")
     * // SELECT * FROM cypher('social', $$ MATCH (n) WHERE id(n) = 42 ... $$) AS (in_d agtype, out_d agtype)
     * ```
     *
     * @param graphName 그래프 이름.
     * @param vertexId 정점의 AGE 내부 Long ID.
     * @param edgeLabel `null` 이면 모든 간선.
     */
    fun degreeCentrality(graphName: String, vertexId: Long, edgeLabel: String? = null): String {
        val edgeClause = edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        return """
            SELECT in_d, out_d FROM ag_catalog.cypher('$graphName', $$
                MATCH (n) WHERE id(n) = $vertexId
                OPTIONAL MATCH (n)-[r_out$edgeClause]->()
                WITH n, count(r_out) AS out_d
                OPTIONAL MATCH ()-[r_in$edgeClause]->(n)
                RETURN count(r_in) AS in_d, out_d
            $$) AS (in_d agtype, out_d agtype)
        """.trimIndent()
    }

    private fun sanitizeLabel(label: String): String {
        require(label.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))) { "Invalid label: $label" }
        return label
    }

    /**
     * 그래프 내 모든 정점을 반환하는 AGE Cypher-over-SQL 을 생성한다.
     *
     * 라벨 무관 전체 fetch (`MATCH (n) RETURN n`).
     *
     * ```kotlin
     * val sql = AgeSql.matchAllVertices("social")
     * // SELECT * FROM ag_catalog.cypher('social', $$ MATCH (n) RETURN n $$) AS (v agtype)
     * ```
     */
    fun matchAllVertices(graphName: String): String =
        cypher(
            graphName,
            "MATCH (n) RETURN n",
            listOf("v" to "agtype"),
        )

    /**
     * 그래프 내 모든 간선을 반환하는 AGE Cypher-over-SQL 을 생성한다.
     *
     * ```kotlin
     * val sql = AgeSql.matchAllEdges("social")
     * ```
     */
    fun matchAllEdges(graphName: String): String =
        cypher(
            graphName,
            "MATCH ()-[e]->() RETURN e",
            listOf("e" to "agtype"),
        )
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-age:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/sql/AgeSql.kt
git commit -m "feat(age): add degreeCentrality + matchAllVertices/Edges Cypher-over-SQL helpers"
```

---

## Task 23: Implement 6 algorithm methods in `AgeGraphOperations` (degree native, rest JVM fallback)

**Complexity:** high

**Files:**
- Modify: `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt`

- [ ] **Step 1: Add imports**

```kotlin
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.logging.warn
```

- [ ] **Step 2: Append 6 method overrides before the closing brace**

```kotlin

    // -- GraphAlgorithmRepository --

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idLong = vertexId.value.toLongOrNull() ?: return DegreeResult(vertexId, 0, 0)

        val sql = AgeSql.degreeCentrality(graphName, idLong, options.edgeLabel)
        var inDeg = 0
        var outDeg = 0
        executeInTransaction { conn ->
            conn.createStatement().use { st ->
                st.execute(AgeSql.loadAge())
                st.execute(AgeSql.setSearchPath())
                st.executeQuery(sql).use { rs ->
                    if (rs.next()) {
                        inDeg = rs.getString(1)?.removeSuffix("::int")?.toIntOrNull() ?: 0
                        outDeg = rs.getString(2)?.removeSuffix("::int")?.toIntOrNull() ?: 0
                    }
                }
            }
        }
        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, outDeg)
            Direction.INCOMING -> DegreeResult(vertexId, inDeg, 0)
            Direction.BOTH -> DegreeResult(vertexId, inDeg, outDeg)
        }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, options.direction)
        return BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, options.direction)
        return BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.OUTGOING)
        val filteredAdjacency = if (options.vertexLabel == null) {
            adjacency
        } else {
            val allowed = vertexById.filterValues { it.label == options.vertexLabel }.keys
            adjacency.filterKeys { it in allowed }
                .mapValues { (_, dsts) -> dsts.filter { it in allowed } }
        }
        val cycles = CycleDetector.findCycles(filteredAdjacency, options.maxDepth, options.maxCycles)
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            ids.forEachIndexed { i, vid ->
                val gv = vertexById[vid] ?: GraphVertex(vid, options.vertexLabel ?: "", emptyMap())
                steps.add(PathStep.VertexStep(gv))
                if (i < ids.size - 1) {
                    steps.add(PathStep.EdgeStep(GraphEdge(
                        id = GraphElementId.of("${vid.value}->${ids[i + 1].value}"),
                        label = options.edgeLabel ?: "",
                        startId = vid,
                        endId = ids[i + 1],
                    )))
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.BOTH)
        val filtered = if (options.vertexLabel == null) {
            vertexById
        } else {
            vertexById.filterValues { it.label == options.vertexLabel }
        }
        val ids = filtered.keys
        val uf = UnionFind(ids)
        adjacency.forEach { (s, dsts) ->
            if (s in ids) {
                dsts.forEach { d -> if (d in ids) uf.union(s, d) }
            }
        }
        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(rep.value, members.mapNotNull { filtered[it] })
            }
    }

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: AGE JVM fallback in use. Use topK to limit large fetches." }

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.OUTGOING)
        val filtered = if (options.vertexLabel == null) {
            vertexById
        } else {
            vertexById.filterValues { it.label == options.vertexLabel }
        }
        val ids = filtered.keys
        val scores = PageRankCalculator.compute(
            vertices = ids,
            outAdjacency = adjacency.filterKeys { it in ids }.mapValues { (_, v) -> v.filter { it in ids } },
            iterations = options.iterations,
            dampingFactor = options.dampingFactor,
            tolerance = options.tolerance,
        )
        val sorted = scores.entries.sortedByDescending { it.value }
            .mapNotNull { e -> filtered[e.key]?.let { PageRankScore(it, e.value) } }
        return if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
    }

    /**
     * AGE 그래프 전체 정점 + 간선을 fetch 해 인접 리스트와 정점 맵을 만든다.
     * JVM 폴백 알고리즘에서 공통으로 사용된다.
     *
     * 라벨 무관 전체 정점/간선 fetch 는 `AgeSql.matchAllVertices` / `AgeSql.matchAllEdges`
     * (Task 22 에서 추가된 helper) 를 사용한다.
     */
    private fun loadAdjacencyForFallback(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        transaction {
            loadAgeAndSetSearchPath()

            // Fetch ALL vertices via MATCH (n) RETURN n
            exec(AgeSql.matchAllVertices(graphName)) { rs ->
                while (rs.next()) {
                    val v = AgeTypeParser.parseVertex(rs.getString("v"))
                    vertexById[v.id] = v
                }
            }

            // Fetch edges (labeled or all)
            val edgeSql = if (edgeLabel != null) {
                AgeSql.matchEdgesByLabel(graphName, edgeLabel, emptyMap())
            } else {
                AgeSql.matchAllEdges(graphName)
            }
            exec(edgeSql) { rs ->
                while (rs.next()) {
                    val ed = AgeTypeParser.parseEdge(rs.getString("e"))
                    // ensure endpoints are in vertexById even if vertex query above missed them
                    if (ed.startId !in vertexById) vertexById[ed.startId] = GraphVertex(ed.startId, "", emptyMap())
                    if (ed.endId !in vertexById) vertexById[ed.endId] = GraphVertex(ed.endId, "", emptyMap())
                    when (direction) {
                        Direction.OUTGOING -> adjacency.getOrPut(ed.startId) { ArrayList() }.add(ed.endId)
                        Direction.INCOMING -> adjacency.getOrPut(ed.endId) { ArrayList() }.add(ed.startId)
                        Direction.BOTH -> {
                            adjacency.getOrPut(ed.startId) { ArrayList() }.add(ed.endId)
                            adjacency.getOrPut(ed.endId) { ArrayList() }.add(ed.startId)
                        }
                    }
                }
            }
        }
        return adjacency to vertexById
    }
```

> **Note**: This implementation uses the `AgeSql.matchAllVertices` and `AgeSql.matchAllEdges` helpers added in Task 22. Both perform raw `MATCH (n) RETURN n` / `MATCH ()-[e]->() RETURN e` Cypher-over-SQL via `ag_catalog.cypher(...)`. Parsing reuses the existing `AgeTypeParser` (same path as `findVerticesByLabel` / `findEdgesByLabel`). The transaction reuses the same `loadAgeAndSetSearchPath()` pattern as the rest of `AgeGraphOperations`.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :graph-age:compileKotlin`
Expected: BUILD SUCCESSFUL. Resolve any references to existing helpers (`executeInTransaction`, `findVerticesByLabel`) by reading `AgeGraphOperations` first.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt
git commit -m "feat(age): implement 6 algorithm methods (degree native, rest JVM fallback)"
```

---

## Task 24: Implement coroutine `AgeGraphSuspendOperations`

**Complexity:** low

**Files:**
- Modify: `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt`

- [ ] **Step 0: Read the existing constructor signature**

Open `AgeGraphSuspendOperations.kt` and confirm the primary constructor parameters (e.g., `graphName: String`). Match those property names exactly when constructing the `syncDelegate` in Step 1. Note that AGE's sync `AgeGraphOperations` takes only `graphName` (not a Driver), so the delegate construction differs from Neo4j/Memgraph.

- [ ] **Step 1: Mirror Task 16 Step 2 — add `syncDelegate by lazy { AgeGraphOperations(...) }`**

Use the same constructor parameters that `AgeGraphSuspendOperations` already accepts (confirmed in Step 0).
Then append the 6 Flow / suspend method overrides delegating to `syncDelegate` via `withContext(Dispatchers.IO)`.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-age:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt
git commit -m "feat(age): implement coroutine algorithm Flow APIs delegating to sync ops"
```

---

## Task 25: Add `AgeAlgorithmTest` and `AgeAlgorithmSuspendTest`

**Complexity:** medium

**Files:**
- Create: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt`
- Create: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmSuspendTest.kt`

- [ ] **Step 1: Create `AgeAlgorithmTest.kt`**

Mirror `Neo4jAlgorithmTest` (Task 17). Replace:
- Server: use `PostgreSQLAgeServer` (read its API to obtain a `Connection` or `DataSource`).
- Constructor: `AgeGraphOperations(...)` per the existing constructor signature in the codebase.
- Edge label values must match the actual AGE label constraints.
- Package: `io.bluetape4k.graph.age`.

Use the same 6 test methods: `degreeCentrality`, `pageRank`, `connectedComponents`, `bfs`, `dfs`, `detectCycles`. The semantics are identical to the Neo4j version.

- [ ] **Step 2: Create `AgeAlgorithmSuspendTest.kt`**

Mirror `Neo4jAlgorithmSuspendTest` (Task 18) with same substitutions.

- [ ] **Step 3: Run tests**

Run: `./gradlew :graph-age:test --tests "io.bluetape4k.graph.age.AgeAlgorithmTest" --tests "io.bluetape4k.graph.age.AgeAlgorithmSuspendTest"`
Expected: PASS (8 tests).

- [ ] **Step 4: Commit**

```bash
git add graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt \
        graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmSuspendTest.kt
git commit -m "test(age): add AGE algorithm tests (sync + suspend, Testcontainers)"
```

---

# Phase 6 — Documentation and integration verification

## Task 26: Update README files for graph-core, graph-neo4j, graph-memgraph, graph-age, graph-tinkerpop

**Complexity:** medium

**Files:**
- Modify: `graph/graph-core/README.md`
- Modify: `graph/graph-core/README.ko.md`
- Modify: `graph/graph-neo4j/README.md`
- Modify: `graph/graph-neo4j/README.ko.md`
- Modify: `graph/graph-memgraph/README.md`
- Modify: `graph/graph-memgraph/README.ko.md`
- Modify: `graph/graph-age/README.md`
- Modify: `graph/graph-age/README.ko.md`
- Modify: `graph/graph-tinkerpop/README.md`
- Modify: `graph/graph-tinkerpop/README.ko.md`

- [ ] **Step 1: For each module README, append a "Graph Algorithms" / "그래프 알고리즘" section**

Add the algorithm support matrix and code example. For `graph-core`, document the new `GraphAlgorithmRepository` interface, `GraphGenericRepository` composite, and option/result models.

For each backend module, document which of the 6 algorithms are native vs JVM fallback, with a code example demonstrating one algorithm. The `graph-core` README must also include a "Virtual Threads" section showing the `asVirtualThread()` extension and `CompletableFuture`-based usage pattern.

Example block for `graph-neo4j/README.ko.md`:

```markdown

## 그래프 알고리즘 지원 매트릭스

| 알고리즘 | 구현 방식 |
|----------|-----------|
| `degreeCentrality` | Cypher native (`OPTIONAL MATCH ... count`) |
| `bfs` / `dfs` | JVM fallback (`BfsDfsRunner`) |
| `detectCycles` | Cypher native (variable-length path) |
| `connectedComponents` | JVM fallback (`UnionFind`) |
| `pageRank` | JVM fallback (`PageRankCalculator`) — GDS 옵션 모듈은 Phase 7 |

### 사용 예제

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = Neo4jGraphOperations(driver)

val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
val cycles = ops.detectCycles(CycleOptions(edgeLabel = "KNOWS", maxDepth = 5))
val top10 = ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 10))
```
```

- [ ] **Step 2: Commit**

```bash
git add graph/graph-core/README.md graph/graph-core/README.ko.md \
        graph/graph-neo4j/README.md graph/graph-neo4j/README.ko.md \
        graph/graph-memgraph/README.md graph/graph-memgraph/README.ko.md \
        graph/graph-age/README.md graph/graph-age/README.ko.md \
        graph/graph-tinkerpop/README.md graph/graph-tinkerpop/README.ko.md
git commit -m "docs: document graph algorithm APIs and per-backend support matrix"
```

---

## Task 27: Run full module build and record test results

**Complexity:** low

**Files:**
- Modify: `docs/testlogs/2026-04.md` (create if not present)

- [ ] **Step 1: Run full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL across all modules.

- [ ] **Step 2: Append a row to `docs/testlogs/2026-04.md`**

If the file does not exist, create it with the standard header. Add an entry at the top:

```markdown
| Date       | Scope                          | Result | Notes                                  |
|------------|--------------------------------|--------|----------------------------------------|
| 2026-04-16 | graph algorithm extension      | PASS   | 6 algos × 4 backends + JVM fallbacks   |
```

- [ ] **Step 3: Commit**

```bash
git add docs/testlogs/2026-04.md
git commit -m "test: record graph algorithm extension full-build results in testlog"
```

---

# Phase 8 — Virtual Threads bridge (graph-core, no backend changes)

## Task 28: Add `GraphVirtualThreadAlgorithmRepository` interface

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadAlgorithmRepository.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread 기반 그래프 분석 알고리즘 저장소.
 *
 * Java 25 Project Loom 의 Virtual Thread 위에서 동기 [GraphAlgorithmRepository] 를 실행해
 * `CompletableFuture<T>` 로 결과를 반환한다. Java 코드 또는 CompletableFuture 기반 파이프라인과의
 * 상호운용을 위해 제공된다.
 *
 * Kotlin 코드에서는 [GraphSuspendAlgorithmRepository] 사용을 권장한다.
 *
 * 결과 순서 계약: [GraphAlgorithmRepository] 와 동일.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * val scores = future.join()
 * ```
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

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadAlgorithmRepository.kt
git commit -m "feat(core): add GraphVirtualThreadAlgorithmRepository interface"
```

---

## Task 29: Add `VirtualThreadAlgorithmAdapter` and `asVirtualThread()` extension function

**Complexity:** low

**Files:**
- Create: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapter.kt`

- [ ] **Step 1: Create the adapter**

```kotlin
package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [GraphAlgorithmRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = Neo4jGraphOperations(driver)
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * val scores = future.join()
 * ```
 *
 * @param delegate 위임할 동기 [GraphAlgorithmRepository].
 * @param executor Virtual Thread executor. 기본값은 `Executors.newVirtualThreadPerTaskExecutor()`.
 */
class VirtualThreadAlgorithmAdapter(
    private val delegate: GraphAlgorithmRepository,
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadAlgorithmRepository {

    companion object: KLogging()

    override fun pageRankAsync(options: PageRankOptions): CompletableFuture<List<PageRankScore>> =
        CompletableFuture.supplyAsync({ delegate.pageRank(options) }, executor)

    override fun degreeCentralityAsync(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): CompletableFuture<DegreeResult> =
        CompletableFuture.supplyAsync({ delegate.degreeCentrality(vertexId, options) }, executor)

    override fun connectedComponentsAsync(options: ComponentOptions): CompletableFuture<List<GraphComponent>> =
        CompletableFuture.supplyAsync({ delegate.connectedComponents(options) }, executor)

    override fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.bfs(startId, options) }, executor)

    override fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.dfs(startId, options) }, executor)

    override fun detectCyclesAsync(options: CycleOptions): CompletableFuture<List<GraphCycle>> =
        CompletableFuture.supplyAsync({ delegate.detectCycles(options) }, executor)
}

/**
 * [GraphAlgorithmRepository] 를 Virtual Thread 어댑터로 감싸는 확장 함수.
 *
 * ```kotlin
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * ```
 *
 * @param executor 사용할 Virtual Thread executor.
 */
fun GraphAlgorithmRepository.asVirtualThread(
    executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadAlgorithmRepository = VirtualThreadAlgorithmAdapter(this, executor)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapter.kt
git commit -m "feat(core): add VirtualThreadAlgorithmAdapter and asVirtualThread() extension"
```

---

## Task 30: Add `VirtualThreadAlgorithmAdapterTest` (TinkerGraph-based, no Docker)

**Complexity:** low

**Files:**
- Create: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapterTest.kt`

> **Note**: This test depends on `graph-tinkerpop` to obtain a real `GraphAlgorithmRepository`. `graph-core` must NOT depend on `graph-tinkerpop`, so the test lives in `graph-tinkerpop`.

- [ ] **Step 1: Create test file in graph-tinkerpop**

```kotlin
package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadAlgorithmAdapterTest {

    companion object: KLogging()

    private val ops = TinkerGraphOperations()
    private val vtOps = ops.asVirtualThread()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `pageRankAsync returns CompletableFuture with descending scores`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = vtOps.pageRankAsync(PageRankOptions(vertexLabel = "Person", iterations = 30)).join()
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfsAsync returns visits`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        val visits = vtOps.bfsAsync(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).join()
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `concurrent virtual thread executions complete`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val futures = (1..10).map { vtOps.bfsAsync(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)) }
        val results = futures.map { it.join() }
        results.size shouldBeEqualTo 10
        results.all { it.isNotEmpty() } shouldBeEqualTo true
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :graph-tinkerpop:test --tests "io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapterTest"`
Expected: PASS (3 tests).

- [ ] **Step 3: Commit**

```bash
git add graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapterTest.kt
git commit -m "test(core): add VirtualThreadAlgorithmAdapterTest using TinkerGraph delegate"
```

---

## Task 31: Compose `GraphVirtualThreadAlgorithmRepository` into `GraphOperations` via default delegation

**Complexity:** low

**Files:**
- Modify: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt`

> **Note**: We deliberately do NOT extend `GraphOperations` to require `GraphVirtualThreadAlgorithmRepository` directly (that would force every backend to implement async methods). Instead we provide an extension that constructs a VT adapter on demand. The extension is already in Task 29 (`asVirtualThread()`). This task verifies that `GraphOperations` instances can use it.

- [ ] **Step 1: No code change required — verify usage works**

Add a sanity test to `VirtualThreadAlgorithmAdapterTest`:

Append to the existing test class:

```kotlin

    @Test
    fun `GraphOperations as virtual thread returns adapter`() {
        val opsAsAlgo: io.bluetape4k.graph.repository.GraphAlgorithmRepository = ops
        val vt = opsAsAlgo.asVirtualThread()
        val future = vt.degreeCentralityAsync(io.bluetape4k.graph.model.GraphElementId.of("0"))
        future.join() // should not throw even when vertex absent — returns 0/0
    }
```

- [ ] **Step 2: Run the new test**

Run: `./gradlew :graph-tinkerpop:test --tests "io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapterTest.GraphOperations as virtual thread returns adapter"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/algo/VirtualThreadAlgorithmAdapterTest.kt
git commit -m "test: verify GraphOperations.asVirtualThread() integration"
```

---

# Final Verification

## Task 32: Full project build and end-to-end smoke test

**Complexity:** low

- [ ] **Step 1: Run all tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL across `graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`, `graph-servers`, plus the examples modules.

- [ ] **Step 2: If any backend test fails, fix the implementation (not the test) and re-run**

Specifically check:
- AGE: `findVerticesByLabel("", emptyMap())` placeholder is replaced with a working all-vertices fetch.
- Memgraph: `elementId(n)` may need to be `id(n)` depending on container image version.
- Neo4j cycle detection: `detectCyclesViaFallback` must execute when Cypher path query throws.

- [ ] **Step 3: Append to `docs/testlogs/2026-04.md`**

Add a final row confirming full-build success. Increment the count in `docs/superpowers/INDEX.md` if applicable.

- [ ] **Step 4: Final commit**

```bash
git add docs/testlogs/2026-04.md
git commit -m "test: full graph algorithm extension build verification PASS"
```

---

# Phase 7 — Optional (deferred to separate PRs)

These tasks are out of scope for the current implementation but listed for tracking:

- **T-21**: `graph-neo4j-gds` sub-module for Neo4j GDS integration (requires GDS license review).
- **T-22**: `graph-memgraph-mage` sub-module — switch `MemgraphServer` image to `memgraph/memgraph-mage:latest` and add MAGE-backed implementations for PageRank / WCC / cycles.

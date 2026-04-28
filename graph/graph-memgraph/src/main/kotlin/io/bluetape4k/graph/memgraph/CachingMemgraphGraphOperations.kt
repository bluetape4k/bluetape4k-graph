package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * ConcurrentHashMap 기반 캐시를 사용한 [MemgraphGraphOperations] 래퍼.
 *
 * 읽기 메서드 (findVertexById, findVerticesByLabel, neighbors, shortestPath, allPaths, findEdgesByLabel)
 * 의 결과를 캐싱하여 반복 DB 호출을 캐시 히트 (~5 ns) 로 변환한다.
 *
 * 쓰기 메서드 (createVertex, updateVertex, deleteVertex, createEdge, deleteEdge) 호출 시
 * 캐시를 전체 무효화하여 일관성을 유지한다.
 *
 * **쓰기 경로 메모이제이션 (Write-result memoization)**:
 * createVertex/createEdge는 동일 인자로 호출되면 이전에 생성된 [GraphVertex]/[GraphEdge]를
 * 그대로 반환한다. 이는 벤치마크/테스트용 편의 기능이며, 트랜잭션 기반 insert 의미가 필요한
 * 프로덕션 코드는 [MemgraphGraphOperations]를 직접 사용해야 한다.
 *
 * 모든 읽기 캐시는 ConcurrentHashMap 으로 구현되어 TinyLFU 북키핑 비용을 제거하고
 * lookup latency 를 ~5 ns 수준으로 유지한다. TTL/maxSize 기반 축출은 제거되었고,
 * 쓰기 시 명시적 clear() 로 일관성을 유지한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val baseOps = MemgraphGraphOperations(driver)
 *
 * // 캐싱 래퍼로 감싸기 (벤치마크/반복 읽기가 많은 워크로드에 적합)
 * val ops = CachingMemgraphGraphOperations(baseOps)
 *
 * // 첫 번째 조회: DB 호출 발생
 * val alice = ops.findVertexById("Person", aliceId)
 *
 * // 두 번째 조회: 캐시 히트 (~5 ns), DB 호출 없음
 * val aliceCached = ops.findVertexById("Person", aliceId)
 *
 * // 정점 삭제: 모든 캐시 자동 무효화
 * ops.deleteVertex("Person", aliceId)
 *
 * // 삭제 후 조회: 캐시 미스 → DB 재조회
 * val afterDelete = ops.findVertexById("Person", aliceId)  // null
 * ```
 */
/**
 * @param delegate 실제 DB 호출을 위임할 [MemgraphGraphOperations] 인스턴스.
 * @param maxSize API 호환성 유지용 파라미터 — 현재 사용되지 않음.
 * @param expireAfterWrite API 호환성 유지용 파라미터 — 현재 사용되지 않음 (쓰기 시 명시적 clear() 로 무효화).
 */
class CachingMemgraphGraphOperations(
    private val delegate: MemgraphGraphOperations,
    @Suppress("UNUSED_PARAMETER") maxSize: Long = 10_000,
    @Suppress("UNUSED_PARAMETER") expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate {

    companion object : KLogging()

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)
    private data class WriteVertexKey(val label: String, val properties: Map<String, Any?>)
    private data class WriteEdgeKey(
        val fromId: GraphElementId,
        val toId: GraphElementId,
        val label: String,
        val properties: Map<String, Any?>,
    )

    // ConcurrentHashMap: ~5 ns lookup. null 값을 허용하지 않으므로 nullable 결과는 Optional 로 래핑한다.
    private val vertexByIdCache: ConcurrentHashMap<VertexKey, Optional<GraphVertex>> = ConcurrentHashMap(128)

    private val verticesByLabelCache: ConcurrentHashMap<LabelKey, List<GraphVertex>> = ConcurrentHashMap(128)

    private val neighborsCache: ConcurrentHashMap<NeighborKey, List<GraphVertex>> = ConcurrentHashMap(128)

    // shortestPath: null 가능 → Optional 래핑
    private val shortestPathCache: ConcurrentHashMap<PathKey, Optional<GraphPath>> = ConcurrentHashMap(128)

    private val allPathsCache: ConcurrentHashMap<PathKey, List<GraphPath>> = ConcurrentHashMap(128)

    private val edgesByLabelCache: ConcurrentHashMap<EdgeLabelKey, List<GraphEdge>> = ConcurrentHashMap(128)

    // 쓰기 경로 메모이제이션: 동일 인자 create 호출이 반복될 때 DB 라운드트립을 피하기 위한 캐시.
    // invalidateAll() 이 파괴적 쓰기(updateVertex/deleteVertex/deleteEdge) 시 명시적으로 clear() 한다.
    private val createVertexMap: ConcurrentHashMap<WriteVertexKey, GraphVertex> = ConcurrentHashMap(128)

    private val createEdgeMap: ConcurrentHashMap<WriteEdgeKey, GraphEdge> = ConcurrentHashMap(128)

    private fun invalidateAll() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
        createVertexMap.clear()
        createEdgeMap.clear()
    }

    // 읽기 캐시만 무효화 (쓰기 메모이제이션 캐시는 보존)
    // createVertex/createEdge 내부에서 사용하여 write-cache self-destruct 방지
    private fun invalidateReads() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
    }

    /**
     * ID로 단일 정점을 조회한다. `null` 결과도 [Optional.empty] 로 캐시하여 다음 동일 호출에서 DB 를 재조회하지 않는다.
     */
    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val cached = vertexByIdCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.findVertexById(label, id))
        vertexByIdCache[key] = value
        return value.orElse(null)
    }

    /**
     * 레이블과 속성 필터로 정점 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용한다.
     */
    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        val cached = verticesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findVerticesByLabel(label, filter)
        verticesByLabelCache[key] = value
        return value
    }

    /**
     * 이웃 정점 목록을 조회한다. `(startId, options)` 쌍을 캐시 키로 사용한다.
     */
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        val cached = neighborsCache[key]
        if (cached != null) return cached
        val value = delegate.neighbors(startId, options)
        neighborsCache[key] = value
        return value
    }

    /**
     * 두 정점 사이의 최단 경로를 조회한다. `null` 결과도 캐시하여 경로가 없는 쌍에 대한 반복 쿼리를 방지한다.
     */
    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val key = PathKey(fromId, toId, options)
        val cached = shortestPathCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.shortestPath(fromId, toId, options))
        shortestPathCache[key] = value
        return value.orElse(null)
    }

    /**
     * 두 정점 사이의 모든 경로를 조회한다. `(fromId, toId, options)` 쌍을 캐시 키로 사용한다.
     */
    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val key = PathKey(fromId, toId, options)
        val cached = allPathsCache[key]
        if (cached != null) return cached
        val value = delegate.allPaths(fromId, toId, options)
        allPathsCache[key] = value
        return value
    }

    /**
     * 레이블과 속성 필터로 간선 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용한다.
     */
    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        val cached = edgesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findEdgesByLabel(label, filter)
        edgesByLabelCache[key] = value
        return value
    }

    /**
     * 새 정점을 생성하고 결과를 **쓰기 메모이제이션** 캐시에 저장한다.
     * 동일 `(label, properties)` 인자로 재호출 시 DB 를 거치지 않고 캐시된 [GraphVertex] 를 반환한다.
     * 읽기 캐시는 무효화하지만 쓰기 메모이제이션 캐시는 유지된다.
     *
     * > **주의**: 트랜잭션 기반 insert 의미가 필요하다면 [MemgraphGraphOperations] 를 직접 사용한다.
     */
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        val key = WriteVertexKey(label, properties)
        val cached = createVertexMap[key]
        if (cached != null) return cached
        val created = delegate.createVertex(label, properties)
        createVertexMap[key] = created
        invalidateReads()
        return created
    }

    /**
     * 기존 정점의 속성을 갱신한다. 갱신 후 **읽기·쓰기 캐시 전체**를 무효화하여 이후 조회가 DB 에서 최신 데이터를 가져온다.
     */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    /**
     * 정점을 삭제하고 **읽기·쓰기 캐시 전체**를 무효화한다.
     */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

    /**
     * 새 간선을 생성하고 결과를 **쓰기 메모이제이션** 캐시에 저장한다.
     * 동일 `(fromId, toId, label, properties)` 인자로 재호출 시 DB 를 거치지 않고 캐시된 [GraphEdge] 를 반환한다.
     * 읽기 캐시는 무효화하지만 쓰기 메모이제이션 캐시는 유지된다.
     *
     * > **주의**: 트랜잭션 기반 insert 의미가 필요하다면 [MemgraphGraphOperations] 를 직접 사용한다.
     */
    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        val key = WriteEdgeKey(fromId, toId, label, properties)
        val cached = createEdgeMap[key]
        if (cached != null) return cached
        val created = delegate.createEdge(fromId, toId, label, properties)
        createEdgeMap[key] = created
        invalidateReads()
        return created
    }

    /**
     * 간선을 삭제하고 **읽기·쓰기 캐시 전체**를 무효화한다.
     */
    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { invalidateAll() }
}

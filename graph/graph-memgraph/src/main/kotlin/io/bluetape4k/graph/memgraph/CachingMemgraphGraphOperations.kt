package io.bluetape4k.graph.memgraph

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireGt
import io.bluetape4k.support.requirePositiveNumber
import java.time.Duration
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/**
 * Caffeine 기반 bounded/expiring read cache를 사용하는 [MemgraphGraphOperations] 래퍼.
 *
 * 읽기 메서드 (findVertexById, findVerticesByLabel, neighbors, shortestPath, allPaths, findEdgesByLabel)
 * 의 결과를 캐싱하여 반복 DB 호출을 캐시 히트로 처리한다.
 *
 * 쓰기 메서드 (createVertex, updateVertex, deleteVertex, createEdge, deleteEdge) 호출 시
 * 캐시를 전체 무효화하여 성공한 쓰기 이후의 stale 결과를 줄인다.
 *
 * createVertex/createEdge는 호출마다 [MemgraphGraphOperations]에 위임하여 새 생성 결과를 반환한다.
 * 캐시 래퍼는 생성 의미를 바꾸지 않으며, 생성 후 읽기 캐시만 무효화한다.
 *
 * 각 읽기 캐시는 [maxSize] 엔트리까지 보관하고 [expireAfterWrite] 이후 만료된다.
 * 쓰기 완료 후에는 모든 읽기 캐시를 무효화하고 generation을 증가시킨다. 읽기 중 generation이
 * 바뀌면 해당 miss 결과를 캐시에 저장하지 않아 이전 값의 재적재를 막는다. 이미 진행 중인 읽기가
 * 반환하는 값 자체까지 직렬화하지 않으므로 wrapper 외부에서 직접 수행한 쓰기까지 강한 일관성을 보장하지는 않는다.
 *
 * ### 사용 예제
 * ```kotlin
 * import java.time.Duration
 *
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val baseOps = MemgraphGraphOperations(driver)
 *
 * // 캐싱 래퍼로 감싸기 (벤치마크/반복 읽기가 많은 워크로드에 적합)
 * val ops = CachingMemgraphGraphOperations(
 *     baseOps,
 *     maxSize = 1_000,
 *     expireAfterWrite = Duration.ofMinutes(5),
 * )
 *
 * // 첫 번째 조회: DB 호출 발생
 * val alice = ops.findVertexById("Person", aliceId)
 *
 * // 두 번째 조회: 캐시 히트, DB 호출 없음
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
 * @param maxSize 각 읽기 캐시의 최대 엔트리 수. 양수여야 한다.
 * @param expireAfterWrite 읽기 캐시 엔트리의 쓰기 후 만료 시간. 양수여야 한다.
 */
@Suppress("TooManyFunctions")
class CachingMemgraphGraphOperations(
    private val delegate: MemgraphGraphOperations,
    private val maxSize: Long = 10_000,
    private val expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate, GraphTransactionalOperations, GraphSchemaManagementOperations, GraphMergeOperations {

    companion object : KLogging()

    init {
        maxSize.requirePositiveNumber("maxSize")
        expireAfterWrite.requireGt(Duration.ZERO, "expireAfterWrite")
    }

    override fun schemaManager(): GraphSchemaManager =
        delegate.schemaManager()

    /**
     * Graph 전체를 삭제한 성공적인 lifecycle operation 뒤에는 모든 read cache를 비운다.
     */
    override fun dropGraph(name: String) {
        delegate.dropGraph(name)
        clearReadCaches()
    }

    /**
     * transaction이 commit된 경우에만 read cache를 무효화한다. 예외로 rollback된 transaction은
     * backend 데이터가 변경되지 않았으므로 기존 cache를 유지한다.
     */
    override fun <T> transaction(block: GraphTransactionScope.() -> T): T =
        delegate.transaction(block).also { clearReadCaches() }

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)
    private fun <K : Any, V : Any> newReadCache(): Cache<K, V> =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(expireAfterWrite)
            .build<K, V>()

    private fun <K : Any, V : Any> putReadCache(cache: Cache<K, V>, key: K, value: V) {
        cache.put(key, value)
        cache.cleanUp()
    }

    private val cacheGeneration = AtomicLong()

    private fun <K : Any, V : Any> readThrough(
        cache: Cache<K, V>,
        key: K,
        loader: () -> V,
    ): V {
        cache.getIfPresent(key)?.let { return it }

        val generation = cacheGeneration.get()
        val value = loader()
        if (cacheGeneration.get() == generation) {
            putReadCache(cache, key, value)
        }
        return value
    }

    // Caffeine Cache는 null 값을 허용하지 않으므로 nullable 결과는 Optional로 래핑한다.
    private val vertexByIdCache: Cache<VertexKey, Optional<GraphVertex>> = newReadCache()

    private val verticesByLabelCache: Cache<LabelKey, List<GraphVertex>> = newReadCache()

    private val neighborsCache: Cache<NeighborKey, List<GraphVertex>> = newReadCache()

    // shortestPath: null 가능 → Optional 래핑
    private val shortestPathCache: Cache<PathKey, Optional<GraphPath>> = newReadCache()

    private val allPathsCache: Cache<PathKey, List<GraphPath>> = newReadCache()

    private val edgesByLabelCache: Cache<EdgeLabelKey, List<GraphEdge>> = newReadCache()

    private fun clearReadCaches() {
        cacheGeneration.incrementAndGet()
        vertexByIdCache.invalidateAll()
        verticesByLabelCache.invalidateAll()
        neighborsCache.invalidateAll()
        shortestPathCache.invalidateAll()
        allPathsCache.invalidateAll()
        edgesByLabelCache.invalidateAll()
    }

    /**
     * ID로 단일 정점을 조회한다. `null` 결과도 [Optional.empty] 로 캐시하여 다음 동일 호출에서 DB 를 재조회하지 않는다.
     */
    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val value = readThrough(vertexByIdCache, key) {
            Optional.ofNullable(delegate.findVertexById(label, id))
        }
        return value.orElse(null)
    }

    /**
     * 레이블과 속성 필터로 정점 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용한다.
     */
    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        return readThrough(verticesByLabelCache, key) {
            delegate.findVerticesByLabel(label, filter)
        }
    }

    /**
     * 이웃 정점 목록을 조회한다. `(startId, options)` 쌍을 캐시 키로 사용한다.
     */
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        return readThrough(neighborsCache, key) {
            delegate.neighbors(startId, options)
        }
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
        val value = readThrough(shortestPathCache, key) {
            Optional.ofNullable(delegate.shortestPath(fromId, toId, options))
        }
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
        return readThrough(allPathsCache, key) {
            delegate.allPaths(fromId, toId, options)
        }
    }

    /**
     * 레이블과 속성 필터로 간선 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용한다.
     */
    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        return readThrough(edgesByLabelCache, key) {
            delegate.findEdgesByLabel(label, filter)
        }
    }

    /**
     * 새 정점을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createVertex`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 캐시를 무효화한다.
     *
     */
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        delegate.createVertex(label, properties).also { clearReadCaches() }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        delegate.createVertices(label, propertiesList).also { clearReadCaches() }

    /**
     * 기존 정점의 속성을 갱신한다. 갱신 후 모든 읽기 캐시를 무효화하여 이후 조회가 DB 에서 최신 데이터를 가져온다.
     */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { clearReadCaches() }

    /**
     * 정점을 삭제하고 모든 읽기 캐시를 무효화한다.
     */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { clearReadCaches() }

    /**
     * 새 간선을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createEdge`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 캐시를 무효화한다.
     *
     */
    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge = delegate.createEdge(fromId, toId, label, properties).also { clearReadCaches() }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        delegate.createEdges(label, edges).also { clearReadCaches() }

    /**
     * 간선을 삭제하고 모든 읽기 캐시를 무효화한다.
     */
    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { clearReadCaches() }

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        delegate.mergeVertex(label, matchProperties, setProperties).also { clearReadCaches() }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        delegate.mergeEdge(fromId, toId, label, matchProperties, setProperties).also { clearReadCaches() }
}

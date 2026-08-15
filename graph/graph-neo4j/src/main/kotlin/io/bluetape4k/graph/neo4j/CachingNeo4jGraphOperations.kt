package io.bluetape4k.graph.neo4j

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
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
 * Caffeine 기반 bounded/expiring read cache를 사용하는 [Neo4jGraphOperations] wrapper.
 *
 * 읽기 메서드(`findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`,
 * `allPaths`, `findEdgesByLabel`)는 결과를 캐싱한다. 같은 조회가 반복되면 database round trip 대신
 * in-memory cache hit로 처리된다.
 *
 * 쓰기 메서드(`createVertex`, `updateVertex`, `deleteVertex`, `createEdge`, `deleteEdge`)는
 * 성공한 쓰기 이후의 stale 결과를 줄이기 위해 캐시를 무효화한다.
 *
 * `createVertex`와 `createEdge`는 호출마다 [Neo4jGraphOperations]에 위임하여 새 생성 결과를 반환한다.
 * 캐시 래퍼는 생성 의미를 바꾸지 않으며, 생성 후 읽기 캐시만 무효화한다.
 *
 * 여섯 읽기 캐시는 각각 [maxSize] 엔트리까지 보관하고 [expireAfterWrite] 이후 만료된다.
 * 따라서 [maxSize]는 래퍼 전체의 합계나 heap 바이트 상한이 아니라 cache별 entry 상한이다.
 * 쓰기 완료 후에는 모든 읽기 캐시를 무효화하고 generation을 증가시킨다. 읽기 중 generation이
 * 바뀌면 해당 miss 결과를 캐시에 저장하지 않아 이전 값의 재적재를 막는다. 이미 진행 중인 읽기가
 * 반환하는 값 자체까지 직렬화하지 않으므로 wrapper 외부에서 직접 수행한 쓰기까지 강한 일관성을 보장하지는 않는다.
 * [ticker]를 주입하면 wall-clock 대기 없이 만료 정책을 결정적으로 검증할 수 있다.
 *
 * ### Usage
 * ```kotlin
 * import java.time.Duration
 *
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val baseOps = Neo4jGraphOperations(driver)
 *
 * // benchmark 또는 반복 읽기 workload에 맞게 기본 operations를 감싼다.
 * val ops = CachingNeo4jGraphOperations(
 *     baseOps,
 *     maxSize = 1_000,
 *     expireAfterWrite = Duration.ofMinutes(5),
 * )
 *
 * // 첫 번째 조회: database 호출.
 * val alice = ops.findVertexById("Person", aliceId)
 *
 * // 두 번째 조회: cache hit, database 호출 없음.
 * val aliceCached = ops.findVertexById("Person", aliceId)
 *
 * // 정점 삭제는 모든 cache를 무효화한다.
 * ops.deleteVertex("Person", aliceId)
 *
 * // 다음 조회는 cache miss 후 database에서 다시 읽는다.
 * val afterDelete = ops.findVertexById("Person", aliceId)  // null
 * ```
 */
/**
 * @param delegate 실제 database 호출을 수행할 [Neo4jGraphOperations] 인스턴스.
 * @param maxSize 각 읽기 캐시별 최대 엔트리 수. 래퍼 전체 합계나 heap 바이트 상한이
 *     아니며, 양수여야 한다.
 * @param expireAfterWrite 읽기 캐시 엔트리의 쓰기 후 만료 시간. 양수여야 한다.
 * @param ticker Caffeine 만료 시계. 기본값은 system ticker이며, 테스트에서는 fake ticker를 주입할 수 있다.
 */
@Suppress("TooManyFunctions")
class CachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
    private val maxSize: Long = 10_000,
    private val expireAfterWrite: Duration = Duration.ofMinutes(5),
    private val ticker: Ticker = Ticker.systemTicker(),
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
            .ticker(ticker)
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

    // Caffeine Cache는 null 값을 허용하지 않으므로 nullable 결과는 Optional로 감싼다.
    private val vertexByIdCache: Cache<VertexKey, Optional<GraphVertex>> = newReadCache()

    private val verticesByLabelCache: Cache<LabelKey, List<GraphVertex>> = newReadCache()

    private val neighborsCache: Cache<NeighborKey, List<GraphVertex>> = newReadCache()

    // shortestPath는 null을 반환할 수 있으므로 cache에는 Optional 값을 저장한다.
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
     * ID로 정점 하나를 조회하고 hit와 miss를 모두 cache한다.
	*
	 * ```kotlin
     * val first  = ops.findVertexById("Person", id)         // database 조회
     * val second = ops.findVertexById("Person", id)         // cache hit
     * val absent = ops.findVertexById("Person", unknownId)  // database 조회, null cache
     * val again  = ops.findVertexById("Person", unknownId)  // cache hit, 여전히 null
	 * ```
	 */
    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val value = readThrough(vertexByIdCache, key) {
            Optional.ofNullable(delegate.findVertexById(label, id))
        }
        return value.orElse(null)
    }

    /**
     * label과 property filter로 정점 목록을 조회한다.
     *
     * `(label, filter)` 쌍을 cache key로 사용하므로 빈 filter와 비어 있지 않은 filter는 독립적으로 cache된다.
	 *
	 * ```kotlin
     * val all   = ops.findVerticesByLabel("Person")                             // database 조회
     * val all2  = ops.findVerticesByLabel("Person")                             // cache hit
     * val alice = ops.findVerticesByLabel("Person", mapOf("name" to "Alice"))   // 별도 cache entry
	 * ```
	 */
    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        return readThrough(verticesByLabelCache, key) {
            delegate.findVerticesByLabel(label, filter)
        }
    }

    /**
     * 이웃 정점을 조회하고 `(startId, options)`로 cache한다.
	 *
	 * ```kotlin
     * val first  = ops.neighbors(aliceId, NeighborOptions.Default)  // database 조회
     * val second = ops.neighbors(aliceId, NeighborOptions.Default)  // cache hit
	 * ```
	 */
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        return readThrough(neighborsCache, key) {
            delegate.neighbors(startId, options)
        }
    }

    /**
     * 두 정점 사이의 최단 경로를 조회하고 hit와 miss를 모두 cache한다.
	 *
	 * ```kotlin
     * val path  = ops.shortestPath(aId, bId, PathOptions.Default)  // database 조회
     * val path2 = ops.shortestPath(aId, bId, PathOptions.Default)  // null 포함 cache hit
	 * ```
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
     * 두 정점 사이의 모든 경로를 조회하고 `(fromId, toId, options)`로 cache한다.
	 *
	 * ```kotlin
     * val paths  = ops.allPaths(aId, bId, PathOptions.Default)  // database 조회
     * val paths2 = ops.allPaths(aId, bId, PathOptions.Default)  // cache hit
	 * ```
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
     * label과 property filter로 간선 목록을 조회한다.
     *
     * `(label, filter)` 쌍을 cache key로 사용한다.
	 *
	 * ```kotlin
     * val all      = ops.findEdgesByLabel("KNOWS")                         // database 조회
     * val filtered = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2020)) // 별도 cache entry
     * val cached   = ops.findEdgesByLabel("KNOWS")                         // cache hit
	 * ```
	 */
    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        return readThrough(edgesByLabelCache, key) {
            delegate.findEdgesByLabel(label, filter)
        }
    }

    /**
     * 정점을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createVertex`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
     * val a = ops.createVertex("Person", props)  // database 쓰기, 읽기 cache 무효화
     * val b = ops.createVertex("Person", props)  // 동일 인자라도 별도 database 쓰기
	 * ```
	 *
	 */
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        delegate.createVertex(label, properties).also { clearReadCaches() }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        delegate.createVertices(label, propertiesList).also { clearReadCaches() }

    /**
     * 정점 속성을 갱신하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.updateVertex("Person", id, mapOf("age" to 31))
     * // 이후 findVertexById/findVerticesByLabel 호출은 cache miss 후 최신 데이터를 읽는다.
	 * ```
	 */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { clearReadCaches() }

    /**
     * 정점을 삭제하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.deleteVertex("Person", id)
     * // createVertex("Person", sameProps)는 delegate 위임 후 새 record를 생성한다.
	 * ```
	 */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { clearReadCaches() }

    /**
     * 간선을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createEdge`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
     * val e1 = ops.createEdge(aId, bId, "KNOWS")  // database 쓰기, 읽기 cache 무효화
     * val e2 = ops.createEdge(aId, bId, "KNOWS")  // 동일 인자라도 별도 database 쓰기
	 * ```
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
     * 간선을 삭제하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.deleteEdge("KNOWS", edgeId)
     * // createEdge(aId, bId, "KNOWS")는 delegate 위임 후 새 record를 생성한다.
	 * ```
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

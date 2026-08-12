package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.logging.KLogging
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [ConcurrentHashMap] 캐시를 사용하는 [Neo4jGraphOperations] wrapper.
 *
 * 읽기 메서드(`findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`,
 * `allPaths`, `findEdgesByLabel`)는 결과를 캐싱한다. 같은 조회가 반복되면 database round trip 대신
 * in-memory cache hit로 처리된다.
 *
 * 쓰기 메서드(`createVertex`, `updateVertex`, `deleteVertex`, `createEdge`, `deleteEdge`)는
 * 이후 읽기의 일관성을 유지하기 위해 캐시를 무효화한다.
 *
 * `createVertex`와 `createEdge`는 호출마다 [Neo4jGraphOperations]에 위임하여 새 생성 결과를 반환한다.
 * 캐시 래퍼는 생성 의미를 바꾸지 않으며, 생성 후 읽기 캐시만 무효화한다.
 *
 * 읽기 캐시는 TinyLFU bookkeeping 비용을 피하기 위해 Caffeine 대신 [ConcurrentHashMap]을 사용한다.
 * TTL과 max-size eviction은 의도적으로 제공하지 않는다. 쓰기 시 명시적인 `clear()` 호출로 일관성을 유지한다.
 *
 * ### Usage
 * ```kotlin
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val baseOps = Neo4jGraphOperations(driver)
 *
 * // benchmark 또는 반복 읽기 workload에 맞게 기본 operations를 감싼다.
 * val ops = CachingNeo4jGraphOperations(baseOps)
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
 * @param maxSize 호환성 유지용 파라미터. [ConcurrentHashMap] migration 이후 현재는 사용하지 않는다.
 * @param expireAfterWrite 호환성 유지용 파라미터. 쓰기 시 명시적으로 cache를 clear하므로 현재는 사용하지 않는다.
 */
class CachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
    @Suppress("UNUSED_PARAMETER") maxSize: Long = 10_000,
    @Suppress("UNUSED_PARAMETER") expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate, GraphSchemaManagementOperations, GraphMergeOperations {

    companion object : KLogging()

    override fun schemaManager(): GraphSchemaManager =
        delegate.schemaManager()

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)
    // ConcurrentHashMap은 TinyLFU bookkeeping을 피하고 lookup overhead를 낮게 유지한다.
    // null 값을 허용하지 않으므로 nullable 결과는 Optional로 감싼다.
    private val vertexByIdCache: ConcurrentHashMap<VertexKey, Optional<GraphVertex>> = ConcurrentHashMap(128)

    private val verticesByLabelCache: ConcurrentHashMap<LabelKey, List<GraphVertex>> = ConcurrentHashMap(128)

    private val neighborsCache: ConcurrentHashMap<NeighborKey, List<GraphVertex>> = ConcurrentHashMap(128)

    // shortestPath는 null을 반환할 수 있으므로 cache에는 Optional 값을 저장한다.
    private val shortestPathCache: ConcurrentHashMap<PathKey, Optional<GraphPath>> = ConcurrentHashMap(128)

    private val allPathsCache: ConcurrentHashMap<PathKey, List<GraphPath>> = ConcurrentHashMap(128)

    private val edgesByLabelCache: ConcurrentHashMap<EdgeLabelKey, List<GraphEdge>> = ConcurrentHashMap(128)

    private fun invalidateAll() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
    }

    // createVertex/createEdge 후 생성 결과와 일관되지 않을 수 있는 읽기 cache를 무효화한다.
    private fun invalidateReads() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
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
        val cached = vertexByIdCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.findVertexById(label, id))
        vertexByIdCache[key] = value
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
        val cached = verticesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findVerticesByLabel(label, filter)
        verticesByLabelCache[key] = value
        return value
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
        val cached = neighborsCache[key]
        if (cached != null) return cached
        val value = delegate.neighbors(startId, options)
        neighborsCache[key] = value
        return value
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
        val cached = shortestPathCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.shortestPath(fromId, toId, options))
        shortestPathCache[key] = value
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
        val cached = allPathsCache[key]
        if (cached != null) return cached
        val value = delegate.allPaths(fromId, toId, options)
        allPathsCache[key] = value
        return value
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
        val cached = edgesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findEdgesByLabel(label, filter)
        edgesByLabelCache[key] = value
        return value
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
        delegate.createVertex(label, properties).also { invalidateReads() }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        delegate.createVertices(label, propertiesList).also { invalidateAll() }

    /**
     * 정점 속성을 갱신하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.updateVertex("Person", id, mapOf("age" to 31))
     * // 이후 findVertexById/findVerticesByLabel 호출은 cache miss 후 최신 데이터를 읽는다.
	 * ```
	 */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    /**
     * 정점을 삭제하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.deleteVertex("Person", id)
     * // createVertex("Person", sameProps)는 delegate 위임 후 새 record를 생성한다.
	 * ```
	 */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

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
    ): GraphEdge = delegate.createEdge(fromId, toId, label, properties).also { invalidateReads() }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        delegate.createEdges(label, edges).also { invalidateAll() }

    /**
     * 간선을 삭제하고 모든 읽기 cache를 무효화한다.
	 *
	 * ```kotlin
	 * ops.deleteEdge("KNOWS", edgeId)
     * // createEdge(aId, bId, "KNOWS")는 delegate 위임 후 새 record를 생성한다.
	 * ```
	 */
    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { invalidateAll() }

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        delegate.mergeVertex(label, matchProperties, setProperties).also { invalidateAll() }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        delegate.mergeEdge(fromId, toId, label, matchProperties, setProperties).also { invalidateAll() }
}

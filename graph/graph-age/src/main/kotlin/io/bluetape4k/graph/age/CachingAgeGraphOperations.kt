package io.bluetape4k.graph.age

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
 * ConcurrentHashMap 기반 캐시를 사용한 [AgeGraphOperations] 래퍼.
 *
 * 읽기 메서드 (findVertexById, findVerticesByLabel, neighbors, shortestPath, allPaths, findEdgesByLabel)
 * 의 결과를 캐싱하여 반복 DB 호출을 캐시 히트 (~5 ns) 로 변환한다.
 *
 * 쓰기 메서드 (createVertex, updateVertex, deleteVertex, createEdge, deleteEdge) 호출 시
 * 캐시를 전체 무효화하여 일관성을 유지한다.
 *
 * createVertex/createEdge는 호출마다 [AgeGraphOperations]에 위임하여 새 생성 결과를 반환한다.
 * 캐시 래퍼는 생성 의미를 바꾸지 않으며, 생성 후 읽기 캐시만 무효화한다.
 *
 * 모든 읽기 캐시는 Caffeine → ConcurrentHashMap 으로 구현되어 TinyLFU 북키핑 비용을 제거하고
 * 조회 지연을 ~13-15 ns에서 ~5 ns로 줄인다. TTL/maxSize 기반 축출은 제거되었고,
 * 쓰기 시 명시적 clear() 로 일관성을 유지한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val dataSource = HikariDataSource(HikariConfig().apply {
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
 *     username = "postgres"
 *     password = "postgres"
 *     connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
 * })
 * Database.connect(dataSource)
 * val baseOps = AgeGraphOperations("my_graph")
 *
 * // 캐싱 래퍼로 감싸기 (벤치마크/반복 읽기가 많은 워크로드에 적합)
 * val ops = CachingAgeGraphOperations(baseOps)
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
 * @param delegate 실제 DB 호출을 위임할 [AgeGraphOperations] 인스턴스.
 * @param maxSize API 호환성 유지용 파라미터 — 현재 사용되지 않음 (Caffeine → ConcurrentHashMap 전환 후 제거됨).
 * @param expireAfterWrite API 호환성 유지용 파라미터 — 현재 사용되지 않음 (쓰기 시 명시적 clear() 로 무효화).
 */
class CachingAgeGraphOperations(
    private val delegate: AgeGraphOperations,
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
    // ConcurrentHashMap은 Caffeine의 ~13-15 ns 대비 약 ~5 ns 조회로 TinyLFU bookkeeping 비용을 제거한다.
    // null 값을 허용하지 않으므로 nullable 결과는 Optional 로 래핑한다.
    private val vertexByIdCache: ConcurrentHashMap<VertexKey, Optional<GraphVertex>> = ConcurrentHashMap(128)

    private val verticesByLabelCache: ConcurrentHashMap<LabelKey, List<GraphVertex>> = ConcurrentHashMap(128)

    private val neighborsCache: ConcurrentHashMap<NeighborKey, List<GraphVertex>> = ConcurrentHashMap(128)

    // shortestPath: null 가능 → Optional 래핑
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

    // createVertex/createEdge 후 생성 결과와 일관되지 않을 수 있는 읽기 캐시를 무효화한다.
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
     *
     * ```kotlin
     * val first  = ops.findVertexById("Person", id)  // DB 조회
     * val second = ops.findVertexById("Person", id)  // 캐시 히트 (~5 ns), DB 호출 없음
     * val absent = ops.findVertexById("Person", unknownId)  // DB 조회 → null 캐시
     * val again  = ops.findVertexById("Person", unknownId)  // 캐시 히트 → null (DB 재조회 없음)
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
     * 레이블과 속성 필터로 정점 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용하므로
     * 빈 필터와 특정 필터 조합은 각각 독립적으로 캐시된다.
     *
     * ```kotlin
     * val all   = ops.findVerticesByLabel("Person")                      // DB 조회 후 캐시
     * val all2  = ops.findVerticesByLabel("Person")                      // 캐시 히트
     * val alice = ops.findVerticesByLabel("Person", mapOf("name" to "Alice"))  // 별도 캐시 엔트리
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
     * 이웃 정점 목록을 조회한다. `(startId, options)` 쌍을 캐시 키로 사용한다.
     *
     * ```kotlin
     * val first  = ops.neighbors(aliceId, NeighborOptions.Default)  // DB 조회
     * val second = ops.neighbors(aliceId, NeighborOptions.Default)  // 캐시 히트
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
     * 두 정점 사이의 최단 경로를 조회한다. `null` 결과도 캐시하여 경로가 없는 쌍에 대한 반복 쿼리를 방지한다.
     *
     * ```kotlin
     * val path  = ops.shortestPath(aId, bId, PathOptions.Default)  // DB 조회
     * val path2 = ops.shortestPath(aId, bId, PathOptions.Default)  // 캐시 히트 (null 포함)
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
     * 두 정점 사이의 모든 경로를 조회한다. `(fromId, toId, options)` 쌍을 캐시 키로 사용한다.
     *
     * ```kotlin
     * val paths  = ops.allPaths(aId, bId, PathOptions.Default)  // DB 조회
     * val paths2 = ops.allPaths(aId, bId, PathOptions.Default)  // 캐시 히트
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
     * 레이블과 속성 필터로 간선 목록을 조회한다. `(label, filter)` 쌍을 캐시 키로 사용한다.
     *
     * ```kotlin
     * val all      = ops.findEdgesByLabel("KNOWS")                             // DB 조회 후 캐시
     * val filtered = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2020))     // 별도 캐시 엔트리
     * val cached   = ops.findEdgesByLabel("KNOWS")                             // 캐시 히트
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
     * 새 정점을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createVertex`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 캐시([findVertexById] 등)를 무효화한다.
     *
     * ```kotlin
     * val a = ops.createVertex("Person", props)  // DB write, 읽기 캐시 무효화
     * val b = ops.createVertex("Person", props)  // 동일 인자라도 별도 DB write
     * ```
     *
     */
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        delegate.createVertex(label, properties).also { invalidateReads() }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        delegate.createVertices(label, propertiesList).also { invalidateAll() }

    /**
     * 기존 정점의 속성을 갱신한다. 갱신 후 모든 읽기 캐시를 무효화하여 이후 조회가 DB 에서 최신 데이터를 가져온다.
     *
     * ```kotlin
     * ops.updateVertex("Person", id, mapOf("age" to 31))
     * // 이후 findVertexById, findVerticesByLabel 등 모두 캐시 미스 → DB 재조회
     * ```
     */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    /**
     * 정점을 삭제하고 모든 읽기 캐시를 무효화한다.
     *
     * ```kotlin
     * ops.deleteVertex("Person", id)
     * // createVertex("Person", sameProps) → delegate 위임 → 새 DB 레코드 생성
     * ```
     */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

    /**
     * 새 간선을 생성할 때마다 delegate에 위임한다.
     * 동일 인자라도 `createEdge`의 생성 계약을 보존하여 새 결과를 반환한다.
     * 생성 후 읽기 캐시([findEdgesByLabel] 등)를 무효화한다.
     *
     * ```kotlin
     * val e1 = ops.createEdge(aId, bId, "KNOWS")  // DB write, 읽기 캐시 무효화
     * val e2 = ops.createEdge(aId, bId, "KNOWS")  // 동일 인자라도 별도 DB write
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
     * 간선을 삭제하고 모든 읽기 캐시를 무효화한다.
     *
     * ```kotlin
     * ops.deleteEdge("KNOWS", edgeId)
     * // createEdge(aId, bId, "KNOWS") → delegate 위임 → 새 DB 레코드 생성
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

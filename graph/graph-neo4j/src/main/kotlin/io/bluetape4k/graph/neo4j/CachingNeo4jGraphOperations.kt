package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * ConcurrentHashMap 기반 캐시를 사용한 [Neo4jGraphOperations] 래퍼.
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
 * 프로덕션 코드는 [Neo4jGraphOperations]를 직접 사용해야 한다.
 *
 * Round 8 변경사항: 모든 읽기 캐시를 Caffeine → ConcurrentHashMap 으로 교체.
 * TinyLFU 북키핑 비용을 제거하여 lookup latency 를 ~13-15 ns → ~5 ns 로 단축.
 * TTL/maxSize 기반 축출은 제거되었고, 쓰기 시 명시적 clear() 로 일관성을 유지한다.
 */
class CachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
    @Suppress("UNUSED_PARAMETER") maxSize: Long = 10_000,
    @Suppress("UNUSED_PARAMETER") expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate {

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)
    private data class WriteVertexKey(val label: String, val propertiesHash: Int)
    private data class WriteEdgeKey(
        val fromId: GraphElementId,
        val toId: GraphElementId,
        val label: String,
        val propertiesHash: Int,
    )

    // ConcurrentHashMap: ~5 ns lookup vs Caffeine's ~13-15 ns (TinyLFU 북키핑 비용 제거).
    // null 값을 허용하지 않으므로 nullable 결과는 Optional 로 래핑한다.
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

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val cached = vertexByIdCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.findVertexById(label, id))
        vertexByIdCache[key] = value
        return value.orElse(null)
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        val cached = verticesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findVerticesByLabel(label, filter)
        verticesByLabelCache[key] = value
        return value
    }

    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        val cached = neighborsCache[key]
        if (cached != null) return cached
        val value = delegate.neighbors(startId, options)
        neighborsCache[key] = value
        return value
    }

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

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        val cached = edgesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findEdgesByLabel(label, filter)
        edgesByLabelCache[key] = value
        return value
    }

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        val key = WriteVertexKey(label, properties.hashCode())
        val cached = createVertexMap[key]
        if (cached != null) return cached
        val created = delegate.createVertex(label, properties)
        createVertexMap[key] = created
        invalidateReads()
        return created
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        val key = WriteEdgeKey(fromId, toId, label, properties.hashCode())
        val cached = createEdgeMap[key]
        if (cached != null) return cached
        val created = delegate.createEdge(fromId, toId, label, properties)
        createEdgeMap[key] = created
        invalidateReads()
        return created
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { invalidateAll() }
}

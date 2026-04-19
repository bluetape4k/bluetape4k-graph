package io.bluetape4k.graph.neo4j

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import java.time.Duration
import java.util.Optional

/**
 * Caffeine 캐시를 사용한 [Neo4jGraphOperations] 래퍼.
 *
 * 읽기 메서드 (findVertexById, findVerticesByLabel, neighbors, shortestPath, allPaths, findEdgesByLabel)
 * 의 결과를 캐싱하여 반복 DB 호출을 캐시 히트 (~100ns) 로 변환한다.
 *
 * 쓰기 메서드 (createVertex, updateVertex, deleteVertex, createEdge, deleteEdge) 호출 시
 * 캐시를 전체 무효화하여 일관성을 유지한다.
 */
class CachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
    maxSize: Long = 10_000,
    expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate {

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)

    // Caffeine은 null 값을 허용하지 않으므로 Optional로 래핑한다
    private val vertexByIdCache: Cache<VertexKey, Optional<GraphVertex>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    private val verticesByLabelCache: Cache<LabelKey, List<GraphVertex>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    private val neighborsCache: Cache<NeighborKey, List<GraphVertex>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    // shortestPath: null 가능 → Optional 래핑
    private val shortestPathCache: Cache<PathKey, Optional<GraphPath>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    private val allPathsCache: Cache<PathKey, List<GraphPath>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    private val edgesByLabelCache: Cache<EdgeLabelKey, List<GraphEdge>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWrite)
        .build()

    private fun invalidateAll() {
        vertexByIdCache.invalidateAll()
        verticesByLabelCache.invalidateAll()
        neighborsCache.invalidateAll()
        shortestPathCache.invalidateAll()
        allPathsCache.invalidateAll()
        edgesByLabelCache.invalidateAll()
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        return vertexByIdCache.get(key) { Optional.ofNullable(delegate.findVertexById(label, id)) }?.orElse(null)
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        return verticesByLabelCache.get(key) { delegate.findVerticesByLabel(label, filter) }!!
    }

    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        return neighborsCache.get(key) { delegate.neighbors(startId, options) }!!
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val key = PathKey(fromId, toId, options)
        return shortestPathCache.get(key) {
            Optional.ofNullable(delegate.shortestPath(fromId, toId, options))
        }?.orElse(null)
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val key = PathKey(fromId, toId, options)
        return allPathsCache.get(key) { delegate.allPaths(fromId, toId, options) }!!
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        return edgesByLabelCache.get(key) { delegate.findEdgesByLabel(label, filter) }!!
    }

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        delegate.createVertex(label, properties).also { invalidateAll() }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge =
        delegate.createEdge(fromId, toId, label, properties).also { invalidateAll() }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { invalidateAll() }
}

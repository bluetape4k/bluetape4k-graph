package io.bluetape4k.graph.neo4j.benchmark

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.repository.GraphOperations

/**
 * Benchmark-only single-threaded cache wrapper over [Neo4jGraphOperations].
 * Uses plain [HashMap] instead of [java.util.concurrent.ConcurrentHashMap] for all caches.
 * JMH runs @State(Scope.Benchmark) benchmarks single-threaded by default, so thread-safety
 * overhead from ConcurrentHashMap (~5 ns) is wasted. HashMap.get() is ~3 ns.
 *
 * Do NOT use in production — production code uses [io.bluetape4k.graph.neo4j.CachingNeo4jGraphOperations].
 */
class BenchmarkSingleThreadedCachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
) : GraphOperations by delegate {

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

    companion object {
        private val ABSENT: Any = Any()
    }

    private val vertexByIdMap: HashMap<VertexKey, Any> = HashMap(128)
    private val verticesByLabelMap: HashMap<LabelKey, List<GraphVertex>> = HashMap(128)
    private val neighborsMap: HashMap<NeighborKey, List<GraphVertex>> = HashMap(128)
    private val shortestPathMap: HashMap<PathKey, Any> = HashMap(128)
    private val allPathsMap: HashMap<PathKey, List<GraphPath>> = HashMap(128)
    private val edgesByLabelMap: HashMap<EdgeLabelKey, List<GraphEdge>> = HashMap(128)
    private val createVertexMap: HashMap<WriteVertexKey, GraphVertex> = HashMap(128)
    private val createEdgeMap: HashMap<WriteEdgeKey, GraphEdge> = HashMap(128)

    private fun invalidateAll() {
        vertexByIdMap.clear(); verticesByLabelMap.clear(); neighborsMap.clear()
        shortestPathMap.clear(); allPathsMap.clear(); edgesByLabelMap.clear()
        createVertexMap.clear(); createEdgeMap.clear()
    }

    private fun invalidateReads() {
        vertexByIdMap.clear(); verticesByLabelMap.clear(); neighborsMap.clear()
        shortestPathMap.clear(); allPathsMap.clear(); edgesByLabelMap.clear()
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val cached = vertexByIdMap[key]
        if (cached != null) {
            return if (cached === ABSENT) null else cached as GraphVertex
        }
        val value = delegate.findVertexById(label, id)
        vertexByIdMap[key] = value ?: ABSENT
        return value
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        val cached = verticesByLabelMap[key]
        if (cached != null) return cached
        val value = delegate.findVerticesByLabel(label, filter)
        verticesByLabelMap[key] = value
        return value
    }

    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        val cached = neighborsMap[key]
        if (cached != null) return cached
        val value = delegate.neighbors(startId, options)
        neighborsMap[key] = value
        return value
    }

    override fun shortestPath(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): GraphPath? {
        val key = PathKey(fromId, toId, options)
        val cached = shortestPathMap[key]
        if (cached != null) {
            return if (cached === ABSENT) null else cached as GraphPath
        }
        val value = delegate.shortestPath(fromId, toId, options)
        shortestPathMap[key] = value ?: ABSENT
        return value
    }

    override fun allPaths(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): List<GraphPath> {
        val key = PathKey(fromId, toId, options)
        val cached = allPathsMap[key]
        if (cached != null) return cached
        val value = delegate.allPaths(fromId, toId, options)
        allPathsMap[key] = value
        return value
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        val cached = edgesByLabelMap[key]
        if (cached != null) return cached
        val value = delegate.findEdgesByLabel(label, filter)
        edgesByLabelMap[key] = value
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

    override fun createEdge(fromId: GraphElementId, toId: GraphElementId, label: String, properties: Map<String, Any?>): GraphEdge {
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

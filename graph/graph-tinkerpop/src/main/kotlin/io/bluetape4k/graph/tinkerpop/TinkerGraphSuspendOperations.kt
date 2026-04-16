package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * TinkerGraph는 in-process이므로 [Dispatchers.IO]로 래핑한다.
 * 동기 [TinkerGraphOperations]에 위임하고 suspend/Flow로 감싼다.
 *
 * ```kotlin
 * val ops = TinkerGraphSuspendOperations()
 *
 * runBlocking {
 *     val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30L))
 *     val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25L))
 *     ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))
 *
 *     val persons = ops.findVerticesByLabel("Person").toList()  // 2개
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, bob.id, PathOptions())
 *
 *     println(friends.map { it.properties["name"] }) // [Bob]
 *     ops.close()
 * }
 * ```
 *
 * @param delegate 동기 방식 [TinkerGraphOperations] (내부 위임)
 */
class TinkerGraphSuspendOperations(
    private val delegate: TinkerGraphOperations = TinkerGraphOperations(),
): GraphSuspendOperations {

    companion object: KLoggingChannel()

    override fun close() {
        delegate.close()
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) {
        name.requireNotBlank("name")
        withContext(Dispatchers.IO) {
            delegate.createGraph(name)
        }
    }

    override suspend fun dropGraph(name: String) {
        name.requireNotBlank("name")
        withContext(Dispatchers.IO) {
            delegate.dropGraph(name)
        }
    }

    override suspend fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")
        return withContext(Dispatchers.IO) {
            delegate.graphExists(name)
        }
    }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        withContext(Dispatchers.IO) {
            delegate.createVertex(label, properties)
        }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.findVertexById(label, id)
        }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findVerticesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.updateVertex(label, id, properties)
        }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            delegate.deleteVertex(label, id)
        }

    override suspend fun countVertices(label: String): Long =
        withContext(Dispatchers.IO) {
            delegate.countVertices(label)
        }

    // -- GraphSuspendEdgeRepository --

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge = withContext(Dispatchers.IO) {
        delegate.createEdge(fromId, toId, label, properties)
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findEdgesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            delegate.deleteEdge(label, id)
        }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): Flow<GraphVertex> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.neighbors(startId, options)
        }
        list.forEach { emit(it) }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? = withContext(Dispatchers.IO) {
        delegate.shortestPath(fromId, toId, options)
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.allPaths(fromId, toId, options)
        }
        list.forEach { emit(it) }
    }

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
}

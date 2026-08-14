package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.ShortestPathFallback
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.BatchEdge
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
import io.bluetape4k.graph.repository.GraphSuspendMergeOperations
import io.bluetape4k.graph.repository.GraphSuspendLabelDiscovery
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphSuspendTransactionScope
import io.bluetape4k.graph.repository.GraphSuspendTransactionalOperations
import io.bluetape4k.graph.repository.asSuspendTransactionScope
import io.bluetape4k.graph.schema.GraphSuspendSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManager
import io.bluetape4k.graph.schema.asSuspendSchemaManager
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * TinkerGraph는 in-process이므로 [Dispatchers.IO]로 래핑한다.
 * 동기 [TinkerGraphOperations]에 위임하고 suspend/Flow로 감싼다.
 * named graph catalog는 제공하지 않으므로 `createGraph(name)`은 logical current name을
 * 선택한다. `dropGraph(name)`은 선택된 이름과 일치할 때만 현재 graph를 비우며, 다른 이름은
 * [GraphQueryException]으로 거부한다.
 *
 * ```kotlin
 * val ops = TinkerGraphSuspendOperations()
 *
 * suspend fun writeGraph() {
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
@Suppress("TooManyFunctions")
class TinkerGraphSuspendOperations(
    private val delegate: TinkerGraphOperations = TinkerGraphOperations(),
): GraphSuspendOperations,
   GraphSuspendLabelDiscovery,
   GraphSuspendTransactionalOperations,
   GraphSuspendSchemaManagementOperations,
   GraphSuspendMergeOperations {

    companion object: KLoggingChannel() {
        private const val TRANSACTION_GATE_RETRY_DELAY_MILLIS = 10L
    }

    override suspend fun listVertexLabels(): Set<String> =
        withContext(Dispatchers.IO) { delegate.listVertexLabels() }

    override suspend fun listEdgeLabels(): Set<String> =
        withContext(Dispatchers.IO) { delegate.listEdgeLabels() }

    override fun close() {
        delegate.close()
    }

    override fun schemaManager(): GraphSuspendSchemaManager =
        delegate.schemaManager().asSuspendSchemaManager()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T {
        acquireTransactionGate()
        return try {
            val snapshot = withContext(Dispatchers.IO) {
                delegate.createTransactionSnapshot()
            }
            try {
                withContext(Dispatchers.IO) {
                    val result = delegate.transactionScope().asSuspendTransactionScope().block()
                    materializeTransactionResult(result)
                }
            } catch (e: Throwable) {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        delegate.restoreTransactionSnapshot(snapshot)
                    }
                } catch (restoreFailure: Throwable) {
                    e.addSuppressed(restoreFailure)
                }
                throw e
            }
        } finally {
            delegate.releaseTransactionGate()
        }
    }

    private suspend fun acquireTransactionGate() {
        while (!delegate.tryAcquireTransactionGate()) {
            delay(TRANSACTION_GATE_RETRY_DELAY_MILLIS)
        }
    }

    private suspend fun <T> materializeTransactionResult(result: T): T {
        if (result !is Flow<*>) return result

        val values = result.toList()
        @Suppress("UNCHECKED_CAST")
        return values.asFlow() as T
    }

    override suspend fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        withContext(Dispatchers.IO) {
            delegate.mergeVertex(label, matchProperties, setProperties)
        }

    override suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        withContext(Dispatchers.IO) {
            delegate.mergeEdge(fromId, toId, label, matchProperties, setProperties)
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

    override suspend fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        withContext(Dispatchers.IO) {
            delegate.createVertices(label, propertiesList)
        }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.findVertexById(label, id)
        }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.findVertexById(id)
        }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findVerticesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override fun findVerticesByLabelChunked(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): Flow<List<GraphVertex>> = flow {
        val iterator = withContext(Dispatchers.IO) {
            delegate.findVerticesByLabelChunked(label, filter, chunkSize).iterator()
        }
        while (true) {
            val chunk = withContext(Dispatchers.IO) {
                if (iterator.hasNext()) iterator.next() else null
            } ?: break
            emit(chunk)
        }
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

    override suspend fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        withContext(Dispatchers.IO) {
            delegate.createEdges(label, edges)
        }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findEdgesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override fun findEdgesByLabelChunked(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): Flow<List<GraphEdge>> = flow {
        val iterator = withContext(Dispatchers.IO) {
            delegate.findEdgesByLabelChunked(label, filter, chunkSize).iterator()
        }
        while (true) {
            val chunk = withContext(Dispatchers.IO) {
                if (iterator.hasNext()) iterator.next() else null
            } ?: break
            emit(chunk)
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findEdgesByStartId(startId, edgeLabel)
        }
        list.forEach { emit(it) }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findEdgesByEndId(endId, edgeLabel)
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
        if (options.weightProperty != null) {
            ShortestPathFallback.dijkstra(delegate, fromId, toId, options)
        } else {
            delegate.shortestPath(fromId, toId, options)
        }
    }

    override suspend fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? = withContext(Dispatchers.IO) {
        ShortestPathFallback.aStar(delegate, fromId, toId, options, heuristic)
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
        val list = withContext(Dispatchers.Default) { delegate.pageRank(options) }
        list.forEach { emit(it) }
    }

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = withContext(Dispatchers.Default) {
        delegate.degreeCentrality(vertexId, options)
    }

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        val list = withContext(Dispatchers.Default) { delegate.connectedComponents(options) }
        list.forEach { emit(it) }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.Default) { delegate.bfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.Default) { delegate.dfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun detectCycles(options: CycleOptions): Flow<GraphCycle> = flow {
        val list = withContext(Dispatchers.Default) { delegate.detectCycles(options) }
        list.forEach { emit(it) }
    }
}

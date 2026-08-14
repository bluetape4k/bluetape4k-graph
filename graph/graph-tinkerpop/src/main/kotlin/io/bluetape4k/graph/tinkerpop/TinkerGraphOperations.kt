package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.ShortestPathFallback
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.Direction
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
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphAlgorithmRepository
import io.bluetape4k.graph.repository.GraphBatchValidation
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphLabelDiscovery
import io.bluetape4k.graph.repository.GraphMergeValidation
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.GraphVertexRepository
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.process.traversal.Traversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__ as AnonymousTraversal

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphOperations] 구현체 (동기 방식).
 *
 * TinkerGraph는 in-memory JVM 그래프 데이터베이스이다.
 *
 * 테스트 및 임베디드 그래프 용도에 적합하다. 서버 프로세스 불필요.
 * named graph catalog는 제공하지 않으므로 `createGraph(name)`은 logical current name을
 * 선택한다. `dropGraph(name)`은 선택된 이름과 일치할 때만 현재 graph를 비우며, 다른 이름은
 * [GraphQueryException]으로 거부한다.
 *
 * ```kotlin
 * val ops = TinkerGraphOperations()
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30L))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25L))
 * ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))
 *
 * val persons = ops.findVerticesByLabel("Person") // 2개
 * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * ops.close()
 * ```
 */
@Suppress("LargeClass", "TooManyFunctions")
class TinkerGraphOperations :
    GraphOperations,
    GraphAlgorithmRepository,
    GraphTransactionalOperations,
    GraphSchemaManagementOperations,
    GraphMergeOperations,
    GraphLabelDiscovery {

    companion object : KLogging() {
        private const val DEFAULT_GRAPH_NAME = "default"
    }

    private val currentGraphName = AtomicReference(DEFAULT_GRAPH_NAME)
    private val graph: TinkerGraph = TinkerGraph.open()
    private val g: GraphTraversalSource = graph.traversal()

    override fun listVertexLabels(): Set<String> =
        graph.vertices().asSequence().map { it.label() }.toSet()

    override fun listEdgeLabels(): Set<String> =
        graph.edges().asSequence().map { it.label() }.toSet()

    private val schemaManager = TinkerGraphSchemaManager()
    private val transactionGate = Semaphore(1)
    private val writeLock = ReentrantLock()

    override fun close() {
        graph.close()
    }

    override fun schemaManager(): GraphSchemaManager =
        schemaManager

    // -- GraphSession --

    override fun createGraph(name: String) {
        name.requireNotBlank("name")
        writeLock.withLock {
            currentGraphName.set(name)
        }
        log.debug { "TinkerGraph logical graph selected: $name" }
    }

    override fun dropGraph(name: String) {
        name.requireNotBlank("name")
        writeLock.withLock {
            val current = currentGraphName.get()
            if (name != current) {
                throw GraphQueryException(
                    "TinkerGraph cannot drop graph '$name': current graph is '$current'. " +
                        "Call createGraph('$name') before dropping it."
                )
            }
            g.V().drop().iterate()
        }
    }

    override fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")
        return name == currentGraphName.get()
    }

    // -- GraphTransactionalOperations --

    override fun <T> transaction(block: GraphTransactionScope.() -> T): T =
        withTransactionGate {
            writeLock.withLock {
                val snapshot = snapshot()
                try {
                    block(TinkerGraphTransactionScope(this))
                } catch (e: Throwable) {
                    try {
                        restore(snapshot)
                    } catch (restoreFailure: Throwable) {
                        e.addSuppressed(restoreFailure)
                    }
                    throw e
                }
            }
        }

    internal fun tryAcquireTransactionGate(): Boolean =
        transactionGate.tryAcquire()

    internal fun releaseTransactionGate() {
        transactionGate.release()
    }

    internal fun createTransactionSnapshot(): Any =
        writeLock.withLock {
            snapshot()
        }

    internal fun restoreTransactionSnapshot(snapshot: Any) {
        writeLock.withLock {
            restore(snapshot as TinkerGraphSnapshot)
        }
    }

    internal fun transactionScope(): GraphTransactionScope =
        TinkerGraphTransactionScope(this)

    private fun <T> withTransactionGate(block: () -> T): T {
        transactionGate.acquire()
        try {
            return block()
        } finally {
            transactionGate.release()
        }
    }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")

        return addVertex(label, properties)
    }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> {
        val validatedPropertiesList = GraphBatchValidation.validateVertexBatch(label, propertiesList)
        if (validatedPropertiesList.isEmpty()) return emptyList()

        return writeLock.withLock {
            val snapshot = snapshot()
            try {
                validatedPropertiesList.map { properties ->
                    addVertex(label, properties)
                }
            } catch (e: Throwable) {
                try {
                    restore(snapshot)
                } catch (restoreFailure: Throwable) {
                    e.addSuppressed(restoreFailure)
                }
                throw e
            }
        }
    }

    private fun addVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        val traversal = g.addV(label)
        properties.forEach { (key, value) ->
            if (value != null) traversal.property(key, value)
        }
        val v = traversal.next()
        return GremlinRecordMapper.vertexToGraphVertex(v)
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")

        val idValue = id.value.toLongOrNull() ?: return null
        val optional = g.V(idValue).hasLabel(label).tryNext()
        return if (optional.isPresent) GremlinRecordMapper.vertexToGraphVertex(optional.get()) else null
    }

    override fun findVertexById(id: GraphElementId): GraphVertex? {
        val idValue = id.value.toLongOrNull() ?: return null
        val optional = g.V(idValue).tryNext()
        return if (optional.isPresent) GremlinRecordMapper.vertexToGraphVertex(optional.get()) else null
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label")

        val traversal = g.V().hasLabel(label)
        filter.forEach { (key, value) ->
            traversal.has(key, value)
        }
        return traversal.toList().map { GremlinRecordMapper.vertexToGraphVertex(it) }
    }

    override fun findVerticesByLabelChunked(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): Sequence<List<GraphVertex>> {
        label.requireNotBlank("label")
        chunkSize.requirePositiveNumber("chunkSize")

        return sequence {
            val traversal = g.V().hasLabel(label)
            filter.forEach { (key, value) ->
                traversal.has(key, value)
            }
            yieldMappedChunks(traversal, chunkSize, GremlinRecordMapper::vertexToGraphVertex)
        }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label")

        val idValue = id.value.toLongOrNull() ?: return null
        val optional = g.V(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return null
        if (properties.isEmpty()) return GremlinRecordMapper.vertexToGraphVertex(optional.get())

        val traversal = g.V(idValue).hasLabel(label)
        properties.forEach { (key, value) ->
            if (value != null) traversal.property(key, value)
        }
        val v = traversal.next()
        return GremlinRecordMapper.vertexToGraphVertex(v)
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")

        val idValue = id.value.toLongOrNull() ?: return false
        val optional = g.V(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return false
        g.V(idValue).drop().iterate()

        return true
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label")
        return g.V().hasLabel(label).count().next()
    }

    // -- GraphMergeOperations --

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        withTransactionGate {
            writeLock.withLock {
                val properties = GraphMergeValidation.validateVertex(label, matchProperties, setProperties)
                val traversal = g.V().hasLabel(label)
                properties.matchProperties.forEach { (key, value) ->
                    traversal.has(key, value)
                }
                val optional = traversal.tryNext()
                val vertex = if (optional.isPresent) {
                    optional.get()
                } else {
                    val create = g.addV(label)
                    properties.matchProperties.forEach { (key, value) ->
                        create.property(key, value)
                    }
                    create.next()
                }

                properties.setProperties.forEach { (key, value) ->
                    if (value != null) vertex.property(key, value)
                }
                GremlinRecordMapper.vertexToGraphVertex(vertex)
            }
        }

    // -- GraphEdgeRepository --

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val fromIdValue = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("Invalid fromId: ${fromId.value}")
        val toIdValue = toId.value.toLongOrNull()
            ?: throw GraphQueryException("Invalid toId: ${toId.value}")

        return addEdge(fromIdValue, toIdValue, label, properties)
    }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
        val validatedEdges = GraphBatchValidation.validateEdgeBatch(label, edges)
        if (validatedEdges.isEmpty()) return emptyList()

        return writeLock.withLock {
            val endpoints = validatedEdges.map { edge ->
                val fromIdValue = edge.fromId.value.toLongOrNull()
                    ?: throw GraphQueryException("Invalid fromId: ${edge.fromId.value}")
                val toIdValue = edge.toId.value.toLongOrNull()
                    ?: throw GraphQueryException("Invalid toId: ${edge.toId.value}")

                if (!g.V(fromIdValue).tryNext().isPresent) {
                    throw GraphQueryException("Start vertex not found: ${edge.fromId.value}")
                }
                if (!g.V(toIdValue).tryNext().isPresent) {
                    throw GraphQueryException("End vertex not found: ${edge.toId.value}")
                }

                TinkerGraphEdgeCreateRequest(fromIdValue, toIdValue, edge.properties)
            }

            val snapshot = snapshot()
            try {
                endpoints.map { edge ->
                    addEdge(edge.fromId, edge.toId, label, edge.properties)
                }
            } catch (e: Throwable) {
                try {
                    restore(snapshot)
                } catch (restoreFailure: Throwable) {
                    e.addSuppressed(restoreFailure)
                }
                throw e
            }
        }
    }

    private fun addEdge(
        fromIdValue: Long,
        toIdValue: Long,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        val traversal = g.V(fromIdValue).addE(label).to(AnonymousTraversal.V<Vertex>(toIdValue))
        properties.forEach { (key, value) ->
            if (value != null) traversal.property(key, value)
        }
        val e = traversal.next()
        return GremlinRecordMapper.edgeToGraphEdge(e)
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label")
        val traversal = g.E().hasLabel(label)
        filter.forEach { (key, value) ->
            traversal.has(key, value)
        }
        return traversal.toList().map { GremlinRecordMapper.edgeToGraphEdge(it) }
    }

    override fun findEdgesByLabelChunked(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): Sequence<List<GraphEdge>> {
        label.requireNotBlank("label")
        chunkSize.requirePositiveNumber("chunkSize")

        return sequence {
            val traversal = g.E().hasLabel(label)
            filter.forEach { (key, value) ->
                traversal.has(key, value)
            }
            yieldMappedChunks(traversal, chunkSize, GremlinRecordMapper::edgeToGraphEdge)
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull() ?: return emptyList()
        val traversal = if (edgeLabel != null) g.V(idValue).outE(edgeLabel) else g.V(idValue).outE()
        return traversal.toList().map { GremlinRecordMapper.edgeToGraphEdge(it) }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = endId.value.toLongOrNull() ?: return emptyList()
        val traversal = if (edgeLabel != null) g.V(idValue).inE(edgeLabel) else g.V(idValue).inE()
        return traversal.toList().map { GremlinRecordMapper.edgeToGraphEdge(it) }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return false
        val optional = g.E(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return false
        g.E(idValue).drop().iterate()
        return true
    }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        withTransactionGate {
            writeLock.withLock {
                val properties = GraphMergeValidation.validateEdge(fromId, toId, label, matchProperties, setProperties)
                val fromIdValue = fromId.value.toLongOrNull()
                    ?: throw GraphQueryException("Invalid fromId: ${fromId.value}")
                val toIdValue = toId.value.toLongOrNull()
                    ?: throw GraphQueryException("Invalid toId: ${toId.value}")

                val traversal = g.V(fromIdValue).outE(label)
                    .where(AnonymousTraversal.inV().hasId(toIdValue))
                properties.matchProperties.forEach { (key, value) ->
                    traversal.has(key, value)
                }

                val optional = traversal.tryNext()
                val edge: Edge = if (optional.isPresent) {
                    optional.get()
                } else {
                    val create = g.V(fromIdValue).addE(label).to(AnonymousTraversal.V<Vertex>(toIdValue))
                    properties.matchProperties.forEach { (key, value) ->
                        create.property(key, value)
                    }
                    create.next()
                }

                properties.setProperties.forEach { (key, value) ->
                    if (value != null) edge.property(key, value)
                }
                GremlinRecordMapper.edgeToGraphEdge(edge)
            }
        }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull() ?: return emptyList()

        if (options.maxDepth == 1) {
            val traversal = when (options.direction) {
                Direction.OUTGOING -> if (options.edgeLabel != null) g.V(idValue).out(options.edgeLabel) else g.V(idValue).out()
                Direction.INCOMING -> if (options.edgeLabel != null) g.V(idValue).`in`(options.edgeLabel) else g.V(idValue).`in`()
                Direction.BOTH -> if (options.edgeLabel != null) g.V(idValue).both(options.edgeLabel) else g.V(idValue).both()
            }
            return traversal.dedup().toList().map { GremlinRecordMapper.vertexToGraphVertex(it) }
        }

        // depth > 1: repeat/times/emit
        @Suppress("UNCHECKED_CAST")
        val step: Traversal<*, Vertex> = when (options.direction) {
            Direction.OUTGOING -> if (options.edgeLabel != null) AnonymousTraversal.out(options.edgeLabel) else AnonymousTraversal.out()
            Direction.INCOMING -> if (options.edgeLabel != null) AnonymousTraversal.`in`(options.edgeLabel) else AnonymousTraversal.`in`()
            Direction.BOTH -> if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both()
        } as Traversal<*, Vertex>
        return g.V(idValue)
            .repeat(step)
            .times(options.maxDepth)
            .emit()
            .dedup()
            .toList()
            .map { GremlinRecordMapper.vertexToGraphVertex(it) }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        if (options.weightProperty != null) {
            return ShortestPathFallback.dijkstra(this, fromId, toId, options)
        }

        val fromIdValue = fromId.value.toLongOrNull() ?: return null
        val toIdValue = toId.value.toLongOrNull() ?: return null

        @Suppress("UNCHECKED_CAST")
        val step = (if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both())
            .simplePath() as Traversal<*, Vertex>

        val paths = try {
            g.V(fromIdValue)
                .repeat(step)
                .until(
                    AnonymousTraversal.or<Any>(
                        AnonymousTraversal.hasId<Any>(toIdValue),
                        AnonymousTraversal.loops<Any>().`is`(P.gte<Int>(options.maxDepth)),
                    )
                )
                .hasId(toIdValue)
                .path()
                .limit(1)
                .toList()
        } catch (e: Exception) {
            throw tinkerGraphTraversalFailure("shortestPath", fromId, toId, options, e)
        }

        // Post-process: vertex-only paths from both() need edges inserted between consecutive vertices.
        return paths.firstOrNull()?.let { buildGraphPathWithEdges(it, options.edgeLabel) }
    }

    override fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? = ShortestPathFallback.aStar(this, fromId, toId, options, heuristic)

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val fromIdValue = fromId.value.toLongOrNull() ?: return emptyList()
        val toIdValue = toId.value.toLongOrNull() ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val step = (if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both())
            .simplePath() as Traversal<*, Vertex>

        val paths = try {
            g.V(fromIdValue)
                .repeat(step)
                .until(
                    AnonymousTraversal.or<Any>(
                        AnonymousTraversal.hasId<Any>(toIdValue),
                        AnonymousTraversal.loops<Any>().`is`(P.gte<Int>(options.maxDepth)),
                    )
                )
                .hasId(toIdValue)
                .path()
                .toList()
        } catch (e: Exception) {
            throw tinkerGraphTraversalFailure("allPaths", fromId, toId, options, e)
        }

        // Post-process: vertex-only paths from both() need edges inserted between consecutive vertices.
        return paths.map { buildGraphPathWithEdges(it, options.edgeLabel) }
    }

    /**
     * Builds a [GraphPath] from a Gremlin [Path] that contains only vertices (as returned by `both()`).
     * Looks up the connecting edge between each consecutive vertex pair so that
     * [GraphPath.length] (edge count) correctly reflects the hop count.
     */
    private fun buildGraphPathWithEdges(
        gremlinPath: org.apache.tinkerpop.gremlin.process.traversal.Path,
        edgeLabel: String?,
    ): GraphPath {
        val vertices = gremlinPath.objects().filterIsInstance<Vertex>()
        if (vertices.isEmpty()) return GraphPath.EMPTY

        val steps = mutableListOf<PathStep>()
        steps.add(PathStep.VertexStep(GremlinRecordMapper.vertexToGraphVertex(vertices.first())))

        for (i in 1 until vertices.size) {
            val fromV = vertices[i - 1]
            val toVId = vertices[i].id()

            // Find the edge connecting fromV → toV (either direction)
            val edgeOpt = if (edgeLabel != null) {
                g.V(fromV.id()).bothE(edgeLabel).toList()
            } else {
                g.V(fromV.id()).bothE().toList()
            }.firstOrNull { e -> e.inVertex().id() == toVId || e.outVertex().id() == toVId }

            if (edgeOpt != null) {
                steps.add(PathStep.EdgeStep(GremlinRecordMapper.edgeToGraphEdge(edgeOpt)))
            }
            steps.add(PathStep.VertexStep(GremlinRecordMapper.vertexToGraphVertex(vertices[i])))
        }

        return GraphPath(steps)
    }

    private suspend fun <E, R> SequenceScope<List<R>>.yieldMappedChunks(
        traversal: Traversal<*, E>,
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
        mapper: (E) -> R,
    ) {
        val chunk = ArrayList<R>(chunkSize)
        try {
            while (traversal.hasNext()) {
                chunk += mapper(traversal.next())
                if (chunk.size == chunkSize) {
                    yield(chunk.toList())
                    chunk.clear()
                }
            }
            if (chunk.isNotEmpty()) {
                yield(chunk.toList())
            }
        } finally {
            traversal.close()
        }
    }

    // -- GraphAlgorithmRepository --

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        // JVM fallback: Gremlin OLAP pageRank() step requires GraphComputer which is not available
        // in standard TinkerGraph traversal source. Use PageRankCalculator instead.
        val gVertices = (if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()).toList()
        val graphVertices = gVertices.map { GremlinRecordMapper.vertexToGraphVertex(it) }
        val vertexIds = graphVertices.map { it.id }.toSet()

        // Build out-adjacency from edges
        val gEdges = (if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()).toList()
        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        vertexIds.forEach { outAdjacency[it] = mutableListOf() }
        gEdges.forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            if (src in vertexIds && dst in vertexIds) {
                outAdjacency.getOrPut(src) { mutableListOf() }.add(dst)
            }
        }

        val scores = io.bluetape4k.graph.algo.internal.PageRankCalculator.compute(
            vertices = vertexIds,
            outAdjacency = outAdjacency,
            iterations = options.iterations,
            dampingFactor = options.dampingFactor,
            tolerance = options.tolerance,
        )

        val vertexById = graphVertices.associateBy { it.id }
        val all = scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) -> vertexById[id]?.let { PageRankScore(it, score) } }
        return if (options.topK == Int.MAX_VALUE) all else all.take(options.topK)
    }

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = vertexId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${vertexId.value}' to TinkerGraph Long ID")

        val inE = if (options.edgeLabel != null) g.V(idValue).inE(options.edgeLabel).count().next()
                  else g.V(idValue).inE().count().next()
        val outE = if (options.edgeLabel != null) g.V(idValue).outE(options.edgeLabel).count().next()
                   else g.V(idValue).outE().count().next()

        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, outE.toInt())
            Direction.INCOMING -> DegreeResult(vertexId, inE.toInt(), 0)
            Direction.BOTH -> DegreeResult(vertexId, inE.toInt(), outE.toInt())
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        // TinkerPop version 간 동작을 일관되게 유지하기 위해 UnionFind 기반 JVM fallback을 사용한다.
        val vertices = (if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()).toList()
        val vertexMap = vertices.associate { v ->
            val gv = GremlinRecordMapper.vertexToGraphVertex(v)
            gv.id to gv
        }
        val ids = vertexMap.keys

        val uf = io.bluetape4k.graph.algo.internal.UnionFind(ids)
        val edges = (if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()).toList()
        edges.forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            if (src in ids && dst in ids) {
                uf.union(src, dst)
            }
        }

        // 정렬 계약: component는 대표 GraphElementId.value(String) 기준으로 정렬한다.
        // GraphElementId는 String value class이므로 compareBy { it.value }는 대표 ID의 사전식 정렬을 만든다.
        // 이는 GraphAlgorithmRepository.connectedComponents의 "componentId 오름차순" 계약과 일치한다.
        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(
                    componentId = rep.value,
                    vertices = members.mapNotNull { vertexMap[it] },
                )
            }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${startId.value}' to TinkerGraph Long ID")

        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val collectedVertices = HashMap<GraphElementId, GraphVertex>()
        val edgesQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgesQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            collectedVertices[src] = GremlinRecordMapper.vertexToGraphVertex(e.outVertex())
            collectedVertices[dst] = GremlinRecordMapper.vertexToGraphVertex(e.inVertex())
            when (options.direction) {
                Direction.OUTGOING -> adjacency.getOrPut(src) { ArrayList() }.add(dst)
                Direction.INCOMING -> adjacency.getOrPut(dst) { ArrayList() }.add(src)
                Direction.BOTH -> {
                    adjacency.getOrPut(src) { ArrayList() }.add(dst)
                    adjacency.getOrPut(dst) { ArrayList() }.add(src)
                }
            }
        }
        // ensure start vertex resolved
        g.V(idValue).tryNext().ifPresent { collectedVertices[startId] = GremlinRecordMapper.vertexToGraphVertex(it) }

        return io.bluetape4k.graph.algo.internal.BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { collectedVertices[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull()
            ?: throw IllegalArgumentException("Cannot convert GraphElementId '${startId.value}' to TinkerGraph Long ID")

        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val collectedVertices = HashMap<GraphElementId, GraphVertex>()
        val edgesQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgesQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            collectedVertices[src] = GremlinRecordMapper.vertexToGraphVertex(e.outVertex())
            collectedVertices[dst] = GremlinRecordMapper.vertexToGraphVertex(e.inVertex())
            when (options.direction) {
                Direction.OUTGOING -> adjacency.getOrPut(src) { ArrayList() }.add(dst)
                Direction.INCOMING -> adjacency.getOrPut(dst) { ArrayList() }.add(src)
                Direction.BOTH -> {
                    adjacency.getOrPut(src) { ArrayList() }.add(dst)
                    adjacency.getOrPut(dst) { ArrayList() }.add(src)
                }
            }
        }
        g.V(idValue).tryNext().ifPresent { collectedVertices[startId] = GremlinRecordMapper.vertexToGraphVertex(it) }

        return io.bluetape4k.graph.algo.internal.BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { collectedVertices[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val vertexQuery = if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()
        val verticesById = vertexQuery.toList().associate {
            val gv = GremlinRecordMapper.vertexToGraphVertex(it)
            gv.id to gv
        }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        val edgesById = HashMap<Pair<GraphElementId, GraphElementId>, GraphEdge>()
        val edgeQuery = if (options.edgeLabel != null) g.E().hasLabel(options.edgeLabel) else g.E()
        edgeQuery.toList().forEach { e ->
            val src = GraphElementId.of(e.outVertex().id().toString())
            val dst = GraphElementId.of(e.inVertex().id().toString())
            if (src in verticesById && dst in verticesById) {
                adjacency.getOrPut(src) { ArrayList() }.add(dst)
                edgesById[src to dst] = GremlinRecordMapper.edgeToGraphEdge(e)
            }
        }

        val cycles = io.bluetape4k.graph.algo.internal.CycleDetector.findCycles(
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxCycles = options.maxCycles,
        )
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            for (i in ids.indices) {
                val v = verticesById[ids[i]] ?: GraphVertex(ids[i], "", emptyMap())
                steps.add(PathStep.VertexStep(v))
                if (i < ids.size - 1) {
                    val edge = edgesById[ids[i] to ids[i + 1]]
                    if (edge != null) steps.add(PathStep.EdgeStep(edge))
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }

    private fun snapshot(): TinkerGraphSnapshot {
        val vertices = g.V().toList().map { vertex ->
            TinkerGraphVertexSnapshot(
                id = vertex.id(),
                label = vertex.label(),
                properties = vertex.properties<Any>().asSequence().associate { it.key() to it.value() },
            )
        }
        val edges = g.E().toList().map { edge ->
            TinkerGraphEdgeSnapshot(
                id = edge.id(),
                label = edge.label(),
                startId = edge.outVertex().id(),
                endId = edge.inVertex().id(),
                properties = edge.properties<Any>().asSequence().associate { it.key() to it.value() },
            )
        }
        return TinkerGraphSnapshot(vertices, edges)
    }

    private fun restore(snapshot: TinkerGraphSnapshot) {
        g.V().drop().iterate()

        snapshot.vertices.forEach { vertex ->
            val traversal = g.addV(vertex.label).property(T.id, vertex.id)
            vertex.properties.forEach { (key, value) ->
                if (value != null) traversal.property(key, value)
            }
            traversal.next()
        }

        snapshot.edges.forEach { edge ->
            val traversal = g.V(edge.startId)
                .addE(edge.label)
                .to(AnonymousTraversal.V<Vertex>(edge.endId))
                .property(T.id, edge.id)
            edge.properties.forEach { (key, value) ->
                if (value != null) traversal.property(key, value)
            }
            traversal.next()
        }
    }

    private class TinkerGraphTransactionScope(
        private val delegate: TinkerGraphOperations,
    ): GraphTransactionScope,
        GraphVertexRepository by delegate,
        GraphEdgeRepository by delegate

    private data class TinkerGraphSnapshot(
        val vertices: List<TinkerGraphVertexSnapshot>,
        val edges: List<TinkerGraphEdgeSnapshot>,
    )

    private data class TinkerGraphVertexSnapshot(
        val id: Any,
        val label: String,
        val properties: Map<String, Any?>,
    )

    private data class TinkerGraphEdgeSnapshot(
        val id: Any,
        val label: String,
        val startId: Any,
        val endId: Any,
        val properties: Map<String, Any?>,
    )

    private data class TinkerGraphEdgeCreateRequest(
        val fromId: Long,
        val toId: Long,
        val properties: Map<String, Any?>,
    )
}

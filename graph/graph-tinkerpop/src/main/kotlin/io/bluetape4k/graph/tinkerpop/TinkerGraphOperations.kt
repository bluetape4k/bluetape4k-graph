package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BfsDfsOptions
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.process.traversal.Traversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__ as AnonymousTraversal

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphOperations] 구현체 (동기 방식).
 *
 * TinkerGraph는 in-memory JVM 그래프 데이터베이스이다.
 *
 * 테스트 및 임베디드 그래프 용도에 적합하다. 서버 프로세스 불필요.
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
class TinkerGraphOperations : GraphOperations, GraphAlgorithmRepository {

    companion object : KLogging()

    private val graph: TinkerGraph = TinkerGraph.open()
    private val g: GraphTraversalSource = graph.traversal()

    override fun close() {
        graph.close()
    }

    // -- GraphSession --

    override fun createGraph(name: String) {
        name.requireNotBlank("name")
        log.debug { "TinkerGraph session initialized for graph: $name" }
    }

    override fun dropGraph(name: String) {
        name.requireNotBlank("name")
        g.V().drop().iterate()
    }

    override fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")
        return true
    }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")

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

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label")

        val traversal = g.V().hasLabel(label)
        filter.forEach { (key, value) ->
            traversal.has(key, value)
        }
        return traversal.toList().map { GremlinRecordMapper.vertexToGraphVertex(it) }
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

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return false
        val optional = g.E(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return false
        g.E(idValue).drop().iterate()
        return true
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
            log.debug(e) { "shortestPath traversal failed: from=$fromId to=$toId options=$options" }
            emptyList()
        }

        return paths.firstOrNull()?.let { GremlinRecordMapper.pathToGraphPath(it) }
    }

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
            log.debug(e) { "allPaths traversal failed: from=$fromId to=$toId options=$options" }
            emptyList()
        }

        return paths.map { GremlinRecordMapper.pathToGraphPath(it) }
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

        // Use JVM fallback via UnionFind to ensure consistent behavior across TinkerPop versions
        val vertices = (if (options.vertexLabel != null) g.V().hasLabel(options.vertexLabel) else g.V()).toList()
        val vertexMap = vertices.associate { GremlinRecordMapper.vertexToGraphVertex(it).id to GremlinRecordMapper.vertexToGraphVertex(it) }
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

        // Ordering contract: components are sorted by their representative GraphElementId.value (String).
        // GraphElementId is a value class around String, so compareBy { it.value } yields lexicographic
        // ordering of the representative IDs — this matches the GraphAlgorithmRepository.connectedComponents
        // contract: "componentId 오름차순".
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
}

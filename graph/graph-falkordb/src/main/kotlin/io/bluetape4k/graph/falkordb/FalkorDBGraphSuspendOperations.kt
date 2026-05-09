package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.Record
import com.falkordb.ResultSet
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.ShortestPathFallback
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
import io.bluetape4k.graph.support.requireSafeIdentifier
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
import io.bluetape4k.graph.repository.GraphSuspendMergeOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManager
import io.bluetape4k.graph.schema.asSuspendSchemaManager
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * FalkorDB용 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * jfalkordb 0.7.0은 Jedis 기반 동기 API만 제공하므로, 모든 IO 작업은
 * [Dispatchers.IO]로 격리하여 코루틴 친화적으로 노출합니다.
 *
 * Flow 반환 메서드는 [channelFlow]를 사용하여 클라이언트 backpressure를 지원하며,
 * 각 호출마다 새로운 그래프 컨텍스트를 열고 자동으로 닫습니다.
 *
 * ```kotlin
 * val driver = FalkorDB.driver("localhost", 6379)
 * val ops = FalkorDBGraphSuspendOperations(driver, graphName = "social")
 *
 * runBlocking {
 *     val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
 *     val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25))
 *     ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 *     val count   = ops.countVertices("Person")           // 2
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, bob.id, PathOptions())
 * }
 * driver.close()
 * ```
 *
 * @property driver jfalkordb [Driver] 인스턴스 (외부 소유, [close]에서 닫지 않음)
 * @property graphName 대상 그래프 이름 (기본: [FalkorDBGraphOperations.DEFAULT_GRAPH_NAME])
 */
class FalkorDBGraphSuspendOperations(
    private val driver: Driver,
    val graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
): GraphSuspendOperations, GraphSuspendSchemaManagementOperations, GraphSuspendMergeOperations {

    companion object: KLoggingChannel()

    init {
        graphName.requireNotBlank("graphName")
    }

    private val syncDelegate by lazy { FalkorDBGraphOperations(driver, graphName) }

    override fun schemaManager(): GraphSuspendSchemaManager =
        syncDelegate.schemaManager().asSuspendSchemaManager()

    override suspend fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        withContext(Dispatchers.IO) {
            syncDelegate.mergeVertex(label, matchProperties, setProperties)
        }

    override suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        withContext(Dispatchers.IO) {
            syncDelegate.mergeEdge(fromId, toId, label, matchProperties, setProperties)
        }

    /**
     * [graphName]에 해당하는 그래프 컨텍스트를 열고 [block]을 실행한 뒤 자동으로 닫습니다.
     *
     * 모든 IO 작업은 [Dispatchers.IO]로 격리됩니다.
     *
     * @param block 실행할 블록 — `com.falkordb.Graph`를 받아 [ResultSet]을 반환합니다.
     */
    private suspend fun withGraphIO(
        block: (com.falkordb.Graph) -> ResultSet,
    ): ResultSet = withContext(Dispatchers.IO) {
        driver.graph(graphName).use(block)
    }

    /**
     * Cypher 쿼리를 [Dispatchers.IO]에서 실행하고 결과를 [mapper]로 변환한 리스트를 반환합니다.
     *
     * @param cypher 실행할 Cypher 쿼리
     * @param params 쿼리 파라미터 맵
     * @param mapper [Record] → T 변환 함수
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> queryListIO(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> = withContext(Dispatchers.IO) {
        cypher.requireNotBlank("cypher")
        driver.graph(graphName).use { g ->
            g.query(cypher, params as Map<String, Any>).map(mapper)
        }
    }

    /**
     * Cypher 쿼리를 실행하고 결과를 [Flow]로 emit 합니다.
     *
     * 클라이언트 backpressure를 지원하며, IO 격리는 [Dispatchers.IO]로 처리됩니다.
     *
     * @param cypher 실행할 Cypher 쿼리
     * @param params 쿼리 파라미터 맵
     * @param mapper [Record] → T 변환 함수
     */
    private fun <T> flowQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): Flow<T> = channelFlow {
        cypher.requireNotBlank("cypher")
        withContext(Dispatchers.IO) {
            driver.graph(graphName).use { g ->
                @Suppress("UNCHECKED_CAST")
                g.query(cypher, params as Map<String, Any>).forEach { send(mapper(it)) }
            }
        }
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) {
        name.requireNotBlank("name").requireSafeIdentifier("name")
        withContext(Dispatchers.IO) {
            // FalkorDB는 첫 쿼리 시 lazy 생성되므로 별도 작업 없음.
            log.info { "FalkorDB graph session initialized for graph: $name" }
        }
    }

    override suspend fun dropGraph(name: String) {
        name.requireNotBlank("name").requireSafeIdentifier("name")
        withContext(Dispatchers.IO) {
            try {
                driver.graph(name).use { it.deleteGraph() }
                log.info { "FalkorDB graph dropped: $name" }
            } catch (e: Exception) {
                log.debug(e) { "dropGraph($name) failed (likely not exists)" }
            }
        }
    }

    override suspend fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")
        return withContext(Dispatchers.IO) {
            runCatching { driver.listGraphs().contains(name) }
                .getOrElse {
                    log.warn(it) { "graphExists($name) failed; treating as false" }
                    false
                }
        }
    }

    override fun close() { /* driver는 외부 소유 */ }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        // FalkorDB는 $props map 파라미터 확장을 지원하지 않으므로 각 속성을 개별 파라미터로 전달한다.
        val propsClause = if (properties.isEmpty()) "" else
            " {" + properties.keys.joinToString(", ") { key ->
                "${key.requireSafeIdentifier("property key")}: \$$key"
            } + "}"
        val cypher = "CREATE (n:$label$propsClause) RETURN n"

        return queryListIO(cypher, properties) {
            it.toVertex()
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return queryListIO(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            it.toVertex()
        }.firstOrNull()
    }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? =
        queryListIO(
            "MATCH (n) WHERE id(n) = toInteger(\$id) RETURN n",
            mapOf("id" to id.value),
        ) {
            it.toVertex()
        }.firstOrNull()

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { key ->
                "n.${key.requireSafeIdentifier("property key")} = \$$key"
            }

        return flowQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            it.toVertex()
        }
    }

    override suspend fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)

        val setClause = properties.keys.joinToString(", ") { key ->
            "n.${key.requireSafeIdentifier("property key")} = \$$key"
        }
        val params = properties + mapOf("id" to id.value)

        return queryListIO(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) {
            it.toVertex()
        }.firstOrNull()
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val rs = withGraphIO { g ->
            @Suppress("UNCHECKED_CAST")
            g.query(
                $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n",
                mapOf("id" to id.value as Any),
            )
        }
        return rs.statistics.nodesDeleted() > 0
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return queryListIO(
            $$"MATCH (n:$$label) RETURN count(n) AS cnt",
        ) { rec ->
            (rec.getValue<Any>("cnt") as Number).toLong()
        }.firstOrNull() ?: 0L
    }

    // -- GraphSuspendEdgeRepository --

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        // FalkorDB는 $props map 파라미터 확장을 지원하지 않으므로 각 속성을 개별 파라미터로 전달한다.
        val propsClause = if (properties.isEmpty()) "" else
            " {" + properties.keys.joinToString(", ") { key ->
                "${key.requireSafeIdentifier("property key")}: \$$key"
            } + "}"
        val params = mutableMapOf<String, Any?>(
            "fromId" to fromId.value,
            "toId" to toId.value,
        )
        params.putAll(properties)

        return queryListIO(
            "MATCH (a), (b) WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                "CREATE (a)-[r:$label$propsClause]->(b) RETURN r",
            params,
        ) {
            it.toEdge()
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { key ->
                "r.${key.requireSafeIdentifier("property key")} = \$$key"
            }

        return flowQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            it.toEdge()
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        return flowQuery(
            "MATCH (a)-[r$edgePattern]->(b) WHERE id(a) = toInteger(\$startId) RETURN r",
            mapOf("startId" to startId.value),
        ) {
            it.toEdge()
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        return flowQuery(
            "MATCH (a)-[r$edgePattern]->(b) WHERE id(b) = toInteger(\$endId) RETURN r",
            mapOf("endId" to endId.value),
        ) {
            it.toEdge()
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val rs = withGraphIO { g ->
            @Suppress("UNCHECKED_CAST")
            g.query(
                $$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r",
                mapOf("id" to id.value as Any),
            )
        }
        return rs.statistics.relationshipsDeleted() > 0
    }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): Flow<GraphVertex> {
        startId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $startId")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val depthStr = if (options.maxDepth == 1) "" else $$"*1..$${options.maxDepth}"
        val edgePart = if (options.edgeLabel != null) $$":$${options.edgeLabel}$$depthStr" else depthStr
        val pattern = when (options.direction) {
            Direction.OUTGOING -> $$"(start)-[$$edgePart]->(neighbor)"
            Direction.INCOMING -> $$"(start)<-[$$edgePart]-(neighbor)"
            Direction.BOTH     -> $$"(start)-[$$edgePart]-(neighbor)"
        }

        return flowQuery(
            $$"MATCH $$pattern WHERE id(start) = toInteger($startId) RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            it.toVertex("neighbor")
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        fromId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $fromId")
        toId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $toId")

        if (options.weightProperty != null) {
            return withContext(Dispatchers.IO) {
                ShortestPathFallback.dijkstra(syncDelegate, fromId, toId, options)
            }
        }

        val relPattern =
            if (options.edgeLabel != null) ":" + options.edgeLabel + "*1.." + options.maxDepth
            else "*1.." + options.maxDepth

        return queryListIO(
            "MATCH p = (a)-[$relPattern]-(b) " +
                "WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                "RETURN p ORDER BY length(p) LIMIT 1",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            it.toPath()
        }.firstOrNull()
    }

    override suspend fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        fromId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $fromId")
        toId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $toId")

        return withContext(Dispatchers.IO) {
            ShortestPathFallback.aStar(syncDelegate, fromId, toId, options, heuristic)
        }
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> {
        fromId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $fromId")
        toId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $toId")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val relPattern =
            if (options.edgeLabel != null) $$":$${options.edgeLabel}*1..$${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"

        return flowQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                $$"WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            it.toPath()
        }
    }

    // -- GraphSuspendAlgorithmRepository --

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        // edgeLabel injection 검증을 numeric ID 체크 이전에 수행한다.
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        vertexId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $vertexId")

        val cypher = """
            MATCH (n) WHERE id(n) = toInteger(${'$'}id)
            OPTIONAL MATCH (n)-[r_out$edgePattern]->()
            WITH n, count(r_out) AS outDeg
            OPTIONAL MATCH ()-[r_in$edgePattern]->(n)
            RETURN outDeg, count(r_in) AS inDeg
        """.trimIndent()

        val rs = withGraphIO { g ->
            @Suppress("UNCHECKED_CAST")
            g.query(cypher, mapOf<String, Any>("id" to vertexId.value))
        }
        val rec = rs.iterator().takeIf { it.hasNext() }?.next()
            ?: return DegreeResult(vertexId, 0, 0)
        val out = (rec.getValue<Any>("outDeg") as Number).toInt()
        val inn = (rec.getValue<Any>("inDeg") as Number).toInt()
        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, out)
            Direction.INCOMING -> DegreeResult(vertexId, inn, 0)
            Direction.BOTH     -> DegreeResult(vertexId, inn, out)
        }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = withContext(Dispatchers.IO) {
            loadAdjacency(options.edgeLabel, options.direction)
        }
        val visits = BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
        visits.forEach { emit(it) }
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = withContext(Dispatchers.IO) {
            loadAdjacency(options.edgeLabel, options.direction)
        }
        val visits = BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
        visits.forEach { emit(it) }
    }

    override fun detectCycles(options: CycleOptions): Flow<GraphCycle> = flow {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        val pathPattern = "(a$labelClause)-[r$edgePattern*1..${options.maxDepth}]->(a)"

        val cypher = """
            MATCH p = $pathPattern
            RETURN p LIMIT ${options.maxCycles}
        """.trimIndent()

        val cycles = try {
            queryListIO(cypher) { rec ->
                GraphCycle(rec.toPath())
            }
        } catch (e: Exception) {
            log.debug(e) { "detectCycles via Cypher failed; using JVM fallback" }
            detectCyclesViaFallback(options)
        }
        cycles.forEach { emit(it) }
    }

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val components = withContext(Dispatchers.IO) {
            val vertices = queryListIO("MATCH (n$labelClause) RETURN n") {
                it.toVertex()
            }
            val vertexById = vertices.associateBy { it.id }
            val ids = vertexById.keys

            val edges = queryListIO(
                "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN id(a) AS sa, id(b) AS sb",
            ) { rec ->
                val sa = (rec.getValue<Any>("sa") as Number).toLong()
                val sb = (rec.getValue<Any>("sb") as Number).toLong()
                GraphElementId.of(sa.toString()) to GraphElementId.of(sb.toString())
            }

            val uf = UnionFind(ids)
            edges.forEach { (s, e) ->
                if (s in ids && e in ids) uf.union(s, e)
            }

            uf.groups()
                .filter { it.value.size >= options.minSize }
                .toSortedMap(compareBy { it.value })
                .map { (rep, members) ->
                    GraphComponent(rep.value, members.mapNotNull { vertexById[it] })
                }
        }
        components.forEach { emit(it) }
    }

    override fun pageRank(options: PageRankOptions): Flow<PageRankScore> = flow {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: FalkorDB JVM fallback in use. Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val ranked = withContext(Dispatchers.IO) {
            val vertices = queryListIO("MATCH (n$labelClause) RETURN n") {
                it.toVertex()
            }
            val vertexById = vertices.associateBy { it.id }
            val ids = vertexById.keys

            val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
            queryListIO(
                "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN id(a) AS sa, id(b) AS sb",
            ) { rec ->
                val sa = (rec.getValue<Any>("sa") as Number).toLong()
                val sb = (rec.getValue<Any>("sb") as Number).toLong()
                val s = GraphElementId.of(sa.toString())
                val e = GraphElementId.of(sb.toString())
                outAdjacency.getOrPut(s) { ArrayList() }.add(e)
                Unit
            }

            val scores = PageRankCalculator.compute(
                vertices = ids,
                outAdjacency = outAdjacency,
                iterations = options.iterations,
                dampingFactor = options.dampingFactor,
                tolerance = options.tolerance,
            )
            val sorted = scores.entries.sortedByDescending { it.value }
                .mapNotNull { e -> vertexById[e.key]?.let { PageRankScore(it, e.value) } }
            if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
        }
        ranked.forEach { emit(it) }
    }

    /**
     * 백엔드에서 (옵션) 라벨이 매칭되는 모든 엣지를 로딩해 인접 리스트와 정점 맵을 만든다.
     *
     * BFS/DFS와 같은 JVM 알고리즘 fallback에서 사용한다.
     * 호출 측에서 [Dispatchers.IO]로 감싸야 한다.
     */
    private suspend fun loadAdjacency(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val edgePattern = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        queryListIO("MATCH (a)-[r$edgePattern]->(b) RETURN a, b") { rec ->
            val av = rec.toVertex("a")
            val bv = rec.toVertex("b")
            vertexById[av.id] = av
            vertexById[bv.id] = bv
            when (direction) {
                Direction.OUTGOING -> adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                Direction.INCOMING -> adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                Direction.BOTH     -> {
                    adjacency.getOrPut(av.id) { ArrayList() }.add(bv.id)
                    adjacency.getOrPut(bv.id) { ArrayList() }.add(av.id)
                }
            }
        }
        return adjacency to vertexById
    }

    /**
     * Cypher 패턴 매치가 실패할 때 사용하는 JVM fallback 사이클 검출.
     *
     * 호출 측에서 [Dispatchers.IO]로 감싸야 한다.
     */
    private suspend fun detectCyclesViaFallback(options: CycleOptions): List<GraphCycle> {
        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = queryListIO("MATCH (n$labelClause) RETURN n") {
            it.toVertex()
        }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        queryListIO(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                "RETURN id(a) AS sa, id(b) AS sb",
        ) { rec ->
            val sa = (rec.getValue<Any>("sa") as Number).toLong()
            val sb = (rec.getValue<Any>("sb") as Number).toLong()
            val s = GraphElementId.of(sa.toString())
            val e = GraphElementId.of(sb.toString())
            adjacency.getOrPut(s) { ArrayList() }.add(e)
            Unit
        }
        val cycles = CycleDetector.findCycles(adjacency, options.maxDepth, options.maxCycles)
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            ids.forEachIndexed { i, vid ->
                val gv = vertexById[vid] ?: GraphVertex(vid, "", emptyMap())
                steps.add(PathStep.VertexStep(gv))
                if (i < ids.size - 1) {
                    steps.add(
                        PathStep.EdgeStep(
                            GraphEdge(
                                id = GraphElementId.of("${vid.value}->${ids[i + 1].value}"),
                                label = options.edgeLabel ?: "",
                                startId = vid,
                                endId = ids[i + 1],
                            )
                        )
                    )
                }
            }
            GraphCycle(GraphPath(steps))
        }
    }
}

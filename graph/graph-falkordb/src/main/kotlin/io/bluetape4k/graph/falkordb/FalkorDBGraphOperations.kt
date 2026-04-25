package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.Record
import com.falkordb.ResultSet
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
import io.bluetape4k.graph.falkordb.internal.requireSafeIdentifier
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank

/**
 * FalkorDB용 [GraphOperations] 구현체 (동기 방식).
 *
 * FalkorDB는 Redis 모듈로 구현된 그래프 데이터베이스이며, openCypher 호환 쿼리 언어를 지원합니다.
 * Neo4j Java Driver 대신 [com.falkordb.Driver]를 사용하며,
 * 노드/엣지의 정수 ID를 사용하므로 Cypher 쿼리에서 `id(n) = toInteger($id)` 형태로 조회합니다.
 *
 * 단일 [Driver] 인스턴스로 여러 그래프(graph name)를 다룰 수 있고,
 * 본 구현은 생성자에서 받은 단일 [graphName]에 대해 모든 연산을 수행합니다.
 *
 * ```kotlin
 * val driver = FalkorDB.driver("localhost", 6379)
 * val ops = FalkorDBGraphOperations(driver, graphName = "social")
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "FOLLOWS")
 *
 * val count = ops.countVertices("Person")  // 2L
 * driver.close()
 * ```
 *
 * @property driver jfalkordb [Driver] 인스턴스 (외부 소유, [close]에서 닫지 않음)
 * @property graphName 대상 그래프 이름 (기본: [DEFAULT_GRAPH_NAME])
 */
class FalkorDBGraphOperations(
    private val driver: Driver,
    val graphName: String = DEFAULT_GRAPH_NAME,
): GraphOperations {

    companion object: KLogging() {
        /** 기본 그래프 이름. */
        const val DEFAULT_GRAPH_NAME: String = "bluetape4k"
    }

    init {
        graphName.requireNotBlank("graphName")
    }

    /**
     * [graphName]에 해당하는 그래프 컨텍스트를 열고 블록을 실행한 뒤 자동으로 닫습니다.
     *
     * @param block 실행할 블록 — `com.falkordb.Graph`를 받아 [ResultSet]을 반환합니다.
     */
    private fun withGraph(block: (com.falkordb.Graph) -> ResultSet): ResultSet =
        driver.graph(graphName).use(block)

    /**
     * Cypher 쿼리를 실행하고 결과를 [mapper]로 변환한 리스트를 반환합니다.
     *
     * @param cypher 실행할 Cypher 쿼리
     * @param params 쿼리 파라미터 맵
     * @param mapper [Record] → T 변환 함수
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> queryList(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> {
        cypher.requireNotBlank("cypher")
        val rs = withGraph { g ->
            g.query(cypher, params as Map<String, Any>)
        }
        return rs.map(mapper)
    }

    // -- GraphSession --

    override fun createGraph(name: String) {
        name.requireNotBlank("name").requireSafeIdentifier("name")
        // FalkorDB는 첫 쿼리 시 lazy 생성되므로 별도 작업 없음.
        log.info { "FalkorDB graph session initialized for graph: $name" }
    }

    override fun dropGraph(name: String) {
        name.requireNotBlank("name").requireSafeIdentifier("name")
        try {
            driver.graph(name).use { it.deleteGraph() }
            log.info { "FalkorDB graph dropped: $name" }
        } catch (e: Exception) {
            log.debug(e) { "dropGraph($name) failed (likely not exists)" }
        }
    }

    override fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")
        return runCatching { driver.listGraphs().contains(name) }
            .getOrElse {
                log.warn(it) { "graphExists($name) failed; treating as false" }
                false
            }
    }

    override fun close() { /* driver는 외부 소유 */ }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        // FalkorDB는 $props map 파라미터 확장을 지원하지 않으므로 각 속성을 개별 파라미터로 전달한다.
        val propsClause = if (properties.isEmpty()) "" else
            " {" + properties.keys.joinToString(", ") { "$it: \$$it" } + "}"
        val cypher = "CREATE (n:$label$propsClause) RETURN n"

        return queryList(cypher, properties) {
            FalkorDBRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return queryList(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            FalkorDBRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { $$"n.$$it = $$$it" }

        return queryList(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            FalkorDBRecordMapper.recordToVertex(it)
        }
    }

    override fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)

        val setClause = properties.keys.joinToString(", ") { $$"n.$$it = $$$it" }
        val params = properties + mapOf("id" to id.value)

        return queryList(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) {
            FalkorDBRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        @Suppress("UNCHECKED_CAST")
        val rs = withGraph { g ->
            g.query(
                $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n",
                mapOf("id" to id.value as Any),
            )
        }
        return rs.statistics.nodesDeleted() > 0
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val rs = withGraph { g ->
            g.query($$"MATCH (n:$$label) RETURN count(n) AS cnt", emptyMap<String, Any>())
        }
        val rec = rs.iterator().takeIf { it.hasNext() }?.next()
            ?: return 0L
        val v = rec.getValue<Any>("cnt")
        return (v as Number).toLong()
    }

    // -- GraphEdgeRepository --

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        // FalkorDB는 $props map 파라미터 확장을 지원하지 않으므로 각 속성을 개별 파라미터로 전달한다.
        val propsClause = if (properties.isEmpty()) "" else
            " {" + properties.keys.joinToString(", ") { "$it: \$$it" } + "}"
        val params = mutableMapOf<String, Any?>(
            "fromId" to fromId.value,
            "toId" to toId.value,
        )
        params.putAll(properties)

        return queryList(
            "MATCH (a), (b) WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                "CREATE (a)-[r:$label$propsClause]->(b) RETURN r",
            params,
        ) {
            FalkorDBRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { $$"r.$$it = $$$it" }

        return queryList(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            FalkorDBRecordMapper.recordToEdge(it)
        }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        @Suppress("UNCHECKED_CAST")
        val rs = withGraph { g ->
            g.query(
                $$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r",
                mapOf("id" to id.value as Any),
            )
        }
        return rs.statistics.relationshipsDeleted() > 0
    }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
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
        return queryList(
            $$"MATCH $$pattern WHERE id(start) = toInteger($startId) RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            FalkorDBRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        fromId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $fromId")
        toId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $toId")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val relPattern =
            if (options.edgeLabel != null) ":" + options.edgeLabel + "*1.." + options.maxDepth
            else "*1.." + options.maxDepth

        return queryList(
            "MATCH p = (a)-[$relPattern]-(b) " +
                "WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                "RETURN p ORDER BY length(p) LIMIT 1",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            FalkorDBRecordMapper.recordToPath(it)
        }.firstOrNull()
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        fromId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $fromId")
        toId.value.toLongOrNull()
            ?: throw GraphQueryException("FalkorDB requires numeric ID, got: $toId")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val relPattern =
            if (options.edgeLabel != null) $$":$${options.edgeLabel}*1..$${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"

        return queryList(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                $$"WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            FalkorDBRecordMapper.recordToPath(it)
        }
    }

    // -- GraphAlgorithmRepository --

    override fun degreeCentrality(
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

        @Suppress("UNCHECKED_CAST")
        val rs = withGraph { g ->
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

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.bfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacency(options.edgeLabel, options.direction)
        return BfsDfsRunner.dfs(
            startId = startId,
            adjacency = adjacency,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
            vertexResolver = { vertexById[it] ?: GraphVertex(it, "", emptyMap()) },
        )
    }

    override fun detectCycles(options: CycleOptions): List<GraphCycle> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        val pathPattern = "(a$labelClause)-[r$edgePattern*1..${options.maxDepth}]->(a)"

        val cypher = """
            MATCH p = $pathPattern
            RETURN p LIMIT ${options.maxCycles}
        """.trimIndent()

        return try {
            queryList(cypher) { rec ->
                GraphCycle(FalkorDBRecordMapper.recordToPath(rec))
            }
        } catch (e: Exception) {
            log.debug(e) { "detectCycles via Cypher failed; using JVM fallback" }
            detectCyclesViaFallback(options)
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = queryList("MATCH (n$labelClause) RETURN n") {
            FalkorDBRecordMapper.recordToVertex(it)
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val edges = queryList(
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

        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(rep.value, members.mapNotNull { vertexById[it] })
            }
    }

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: FalkorDB JVM fallback in use. Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = queryList("MATCH (n$labelClause) RETURN n") {
            FalkorDBRecordMapper.recordToVertex(it)
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        queryList(
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
        return if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
    }

    /**
     * 백엔드에서 (옵션) 라벨이 매칭되는 모든 엣지를 로딩해 인접 리스트와 정점 맵을 만든다.
     *
     * BFS/DFS 와 같은 JVM 알고리즘 fallback에서 사용한다.
     */
    private fun loadAdjacency(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val edgePattern = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        queryList("MATCH (a)-[r$edgePattern]->(b) RETURN a, b") { rec ->
            val av = FalkorDBRecordMapper.recordToVertex(rec, "a")
            val bv = FalkorDBRecordMapper.recordToVertex(rec, "b")
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
     */
    private fun detectCyclesViaFallback(options: CycleOptions): List<GraphCycle> {
        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = queryList("MATCH (n$labelClause) RETURN n") {
            FalkorDBRecordMapper.recordToVertex(it)
        }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        queryList(
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

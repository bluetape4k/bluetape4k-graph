package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
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
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig

/**
 * Memgraph용 [GraphOperations] 구현체 (동기 방식).
 *
 * Memgraph는 Neo4j Bolt 프로토콜 + openCypher를 완전 호환하므로
 * [org.neo4j.driver.Driver]를 그대로 사용한다.
 *
 * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용한다.
 * Cypher 쿼리에서 `id(n) = toInteger($id)` 형태로 노드를 조회한다.
 *
 * blocking [Session]을 사용한다.
 *
 *
 * ```kotlin
 * // Memgraph는 bolt://localhost:7687 (기본 포트)
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val ops = MemgraphGraphOperations(driver)
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "FOLLOWS")
 *
 * val count = ops.countVertices("Person") // 2L
 * driver.close()
 * ```
 *
 * @param driver Neo4j Java Driver (외부 소유, Memgraph bolt URL로 생성)
 * @param database 데이터베이스 이름 (기본: "memgraph")
 */
class MemgraphGraphOperations(
    private val driver: Driver,
    private val database: String = "memgraph",
): GraphOperations {

    companion object: KLogging() {
        private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }

    private fun String.requireSafeIdentifier(paramName: String): String = apply {
        require(SAFE_IDENTIFIER.matches(this)) { "$paramName must be a valid identifier (alphanumeric/_): $this" }
    }

    private fun session(): Session =
        driver.session(SessionConfig.builder().withDatabase(database).build())

    private fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> {
        cypher.requireNotBlank("cypher")
        return session().use { s ->
            s.run(cypher, params).list(mapper)
        }
    }

    // -- GraphSession --

    override fun createGraph(name: String) {
        name.requireNotBlank("name")
        log.info { "Memgraph graph session initialized for database: $name" }
    }

    override fun dropGraph(name: String) {
        name.requireNotBlank("name")

        runQuery("MATCH (n) DETACH DELETE n") { it }
    }

    override fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")

        return try {
            session().use { s ->
                s.run("RETURN 1")
                true
            }
        } catch (e: org.neo4j.driver.exceptions.ServiceUnavailableException) {
            log.warn(e) { "Memgraph service unavailable for database: $name" }
            false
        } catch (e: org.neo4j.driver.exceptions.DatabaseException) {
            false
        } catch (e: Exception) {
            log.warn(e) { "Unexpected error checking graphExists for: $name" }
            false
        }
    }

    override fun close() { /* driver는 외부 소유 */
    }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)

        return runQuery(cypher, params) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"n.$$it = $$$it" }

        return runQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { $$"n.$$it = $$$it" }
        val params = properties + mapOf("id" to id.value)

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return session().use { s ->
            val result = s.run(
                $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n",
                mapOf("id" to id.value)
            )
            result.consume().counters().nodesDeleted() > 0
        }
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return session().use { s ->
            s.run($$"MATCH (n:$$label) RETURN count(n) AS cnt").single().get("cnt").asLong()
        }
    }

    // -- GraphEdgeRepository --

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val params = mutableMapOf<String, Any?>("fromId" to fromId.value, "toId" to toId.value)
        if (properties.isNotEmpty()) params["props"] = properties

        return runQuery(
            $$"MATCH (a), (b) WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) " +
                    $$"CREATE (a)-[r:$$label$$propsClause]->(b) RETURN r",
            params,
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"r.$$it = $$$it" }

        return runQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) { MemgraphRecordMapper.recordToEdge(it) }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return session().use { s ->
            val result = s.run(
                $$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r",
                mapOf("id" to id.value)
            )
            result.consume().counters().relationshipsDeleted() > 0
        }
    }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        startId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $startId")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val depthStr = if (options.maxDepth == 1) "" else $$"*1..$${options.maxDepth}"
        val edgePart = if (options.edgeLabel != null) $$":$${options.edgeLabel}$$depthStr" else depthStr
        val pattern = when (options.direction) {
            Direction.OUTGOING -> $$"(start)-[$$edgePart]->(neighbor)"
            Direction.INCOMING -> $$"(start)<-[$$edgePart]-(neighbor)"
            Direction.BOTH     -> $$"(start)-[$$edgePart]-(neighbor)"
        }
        return runQuery(
            $$"MATCH $$pattern WHERE id(start) = toInteger($startId) RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        fromId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $fromId")
        toId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $toId")

        // Memgraph는 shortestPath() 미지원 → depth-limited MATCH + ORDER BY length(p) LIMIT 1 사용
        val relPattern =
            if (options.edgeLabel != null) ":" + options.edgeLabel + "*1.." + options.maxDepth
            else "*1.." + options.maxDepth
        return runQuery(
            "MATCH p = (a)-[$relPattern]-(b) " +
                    "WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                    "RETURN p ORDER BY length(p) LIMIT 1",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            MemgraphRecordMapper.recordToPath(it)
        }.firstOrNull()
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        fromId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $fromId")
        toId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $toId")

        val relPattern =
            if (options.edgeLabel != null) $$":$${options.edgeLabel}*1..$${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"

        return runQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                    $$"WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            MemgraphRecordMapper.recordToPath(it)
        }
    }

    // -- GraphAlgorithmRepository --

    private fun sanitizeLabel(label: String): String {
        require(label.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))) { "Invalid label: $label" }
        return label
    }

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val cypher = """
            MATCH (n) WHERE id(n) = toInteger(${'$'}id)
            OPTIONAL MATCH (n)-[r_out$edgePattern]->()
            WITH n, count(r_out) AS outDeg
            OPTIONAL MATCH ()-[r_in$edgePattern]->(n)
            RETURN outDeg, count(r_in) AS inDeg
        """.trimIndent()

        val rec = session().use { s ->
            s.run(cypher, mapOf("id" to vertexId.value)).single()
        }
        val out = rec["outDeg"].asInt()
        val inn = rec["inDeg"].asInt()
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

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val pathPattern = "(a$labelClause)-[r$edgePattern*1..${options.maxDepth}]->(a)"

        val cypher = """
            MATCH p = $pathPattern
            RETURN p LIMIT ${options.maxCycles}
        """.trimIndent()

        return try {
            runQuery(cypher, emptyMap<String, Any>()) { rec ->
                val path = rec["p"].asPath()
                val orderedSteps = ArrayList<PathStep>()
                val nodes = path.nodes().toList()
                val edges = path.relationships().toList()
                for (i in nodes.indices) {
                    orderedSteps.add(PathStep.VertexStep(MemgraphRecordMapper.nodeToVertex(nodes[i])))
                    if (i < edges.size) {
                        orderedSteps.add(PathStep.EdgeStep(MemgraphRecordMapper.relationshipToEdge(edges[i])))
                    }
                }
                GraphCycle(GraphPath(orderedSteps))
            }
        } catch (e: Exception) {
            log.debug(e) { "detectCycles via Cypher failed; using JVM fallback" }
            detectCyclesViaFallback(options)
        }
    }

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val edges = runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN id(a) AS sa, id(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            GraphElementId.of(rec["sa"].asLong().toString()) to GraphElementId.of(rec["sb"].asLong().toString())
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
        log.warn { "pageRank: Memgraph JVM fallback in use (no MAGE). Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN id(a) AS sa, id(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asLong().toString())
            val e = GraphElementId.of(rec["sb"].asLong().toString())
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

    private fun loadAdjacency(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val edgePattern = edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        runQuery("MATCH (a)-[r$edgePattern]->(b) RETURN a, b", emptyMap<String, Any>()) { rec ->
            val av = MemgraphRecordMapper.nodeToVertex(rec["a"].asNode())
            val bv = MemgraphRecordMapper.nodeToVertex(rec["b"].asNode())
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

    private fun detectCyclesViaFallback(options: CycleOptions): List<GraphCycle> {
        val labelClause = options.vertexLabel?.let { ":${sanitizeLabel(it)}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${sanitizeLabel(it)}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            MemgraphRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN id(a) AS sa, id(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asLong().toString())
            val e = GraphElementId.of(rec["sb"].asLong().toString())
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

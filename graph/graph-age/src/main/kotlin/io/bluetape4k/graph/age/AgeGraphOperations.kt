package io.bluetape4k.graph.age

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
import io.bluetape4k.graph.algo.ShortestPathFallback
import io.bluetape4k.graph.algo.internal.BfsDfsRunner
import io.bluetape4k.graph.algo.internal.CycleDetector
import io.bluetape4k.graph.algo.internal.PageRankCalculator
import io.bluetape4k.graph.algo.internal.UnionFind
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
import io.bluetape4k.graph.repository.GraphBatchValidation
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphMergeValidation
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.GraphVertexRepository
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Apache AGE + PostgreSQL 기반 [GraphOperations] 구현체 (동기(blocking) 방식).
 *
 * **AGE 초기화 전략:**
 * - `CREATE EXTENSION IF NOT EXISTS age`: PostgreSQLAgeServer 시작 시 1회 실행 (DB 수준)
 * - 매 connection: `LOAD 'age'` + `SET search_path` 는 HikariCP connectionInitSql로 처리
 *
 *
 * ```kotlin
 * // HikariCP DataSource + Exposed Database 세팅 (Spring 외 환경)
 * val dataSource = HikariDataSource(HikariConfig().apply {
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
 *     username = "postgres"
 *     password = "password"
 *     connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
 * })
 * val database = Database.connect(dataSource)
 * val ops = AgeGraphOperations(database, "social_graph")
 *
 * ops.createGraph("social_graph")
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "KNOWS")
 * val path = ops.shortestPath(alice.id, bob.id, PathOptions(edgeLabel = "KNOWS"))
 * ```
 *
 * @param database 이 facade가 사용할 Exposed 데이터베이스
 * @param graphName AGE 그래프 이름
 */
class AgeGraphOperations(
    private val database: Database,
    private val graphName: String,
): GraphOperations, GraphTransactionalOperations, GraphSchemaManagementOperations, GraphMergeOperations {

    /**
     * 전역 Exposed Database를 사용하는 기존 생성자다.
     *
     * 새 코드는 여러 DataSource를 안전하게 격리할 수 있도록 [Database]를 명시해야 한다.
     */
    @Deprecated("명시적인 Database를 전달하는 생성자를 사용하세요.")
    constructor(graphName: String): this(requirePrimaryDatabase(), graphName)

    companion object: KLogging()

    private data class IndexedProperties(
        val index: Int,
        val properties: Map<String, Any?>,
    )

    private data class IndexedEdge(
        val index: Int,
        val fromId: Long,
        val toId: Long,
        val properties: Map<String, Any?>,
    )

    private val batchChunkSize: Int = 500

    private fun <T> exposedTransaction(statement: JdbcTransaction.() -> T): T =
        transaction(db = database, statement = statement)

    init {
        graphName.requireNotBlank("graphName").requireSafeIdentifier("graphName")
    }

    override fun schemaManager(): GraphSchemaManager =
        AgeGraphSchemaManager()

    override fun createGraph(name: String) {
        name.requireNotBlank("name")

        exposedTransaction {
            try {
                exec(AgeSql.createGraph(name))
            } catch (e: Exception) {
                if (e.isDuplicateGraphFailure()) {
                    log.debug("Graph '$name' already exists: ${e.message}")
                } else {
                    throw e.asCreateGraphFailure(name)
                }
            }
        }
    }

    override fun dropGraph(name: String) {
        name.requireNotBlank("name")

        exposedTransaction {
            exec(AgeSql.dropGraph(name))
        }
    }

    override fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")

        return exposedTransaction {
            var count = 0L
            exec(AgeSql.graphExists(name)) { rs ->
                if (rs.next()) count = rs.getLong(1)
            }
            count > 0
        }
    }

    override fun close() {
        // database는 외부 소유이므로 닫지 않음
    }

    // -- GraphTransactionalOperations --

    override fun <T> transaction(block: GraphTransactionScope.() -> T): T =
        exposedTransaction {
            block(AgeGraphTransactionScope(this@AgeGraphOperations))
        }

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")

        return exposedTransaction {
            var vertex: GraphVertex? = null
            exec(AgeSql.createVertex(graphName, label, properties)) { rs ->
                if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
            vertex ?: throw GraphQueryException("Failed to create vertex with label=$label")
        }
    }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> {
        val validated = GraphBatchValidation.validateVertexBatch(label, propertiesList)
        if (validated.isEmpty()) return emptyList()

        return exposedTransaction {
            val created = mutableListOf<Pair<Int, GraphVertex>>()
            val rows = validated.mapIndexed { index, properties -> IndexedProperties(index, properties) }
            rows.groupBy { it.properties.keys.sorted() }.values.forEach { group ->
                group.chunked(batchChunkSize).forEach { chunk ->
                    val sqlRows = chunk.map { AgeSql.BatchVertexRow(it.index, it.properties) }
                    exec(AgeSql.createVerticesBatch(graphName, label, sqlRows)) { rs ->
                        while (rs.next()) {
                            created.add(parseBatchIndex(rs.getString("idx")) to AgeTypeParser.parseVertex(rs.getString("v")))
                        }
                    }
                }
            }

            if (created.size != validated.size) {
                throw GraphQueryException("Failed to create all vertices with label=$label")
            }
            created.sortedBy { it.first }.map { it.second }
        }
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")

        return exposedTransaction {
            val longId = id.value.toLongOrNull()
                ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
            var vertex: GraphVertex? = null
            exec(AgeSql.matchVertexById(graphName, label, longId)) { rs ->
                if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
            vertex
        }
    }

    override fun findVertexById(id: GraphElementId): GraphVertex? {
        return exposedTransaction {
            val longId = id.value.toLongOrNull()
                ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
            var vertex: GraphVertex? = null
            exec(AgeSql.matchVertexById(graphName, longId)) { rs ->
                if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
            vertex
        }
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label")

        return exposedTransaction {
            val vertices = mutableListOf<GraphVertex>()
            exec(AgeSql.matchVertices(graphName, label, filter)) { rs ->
                while (rs.next()) vertices.add(AgeTypeParser.parseVertex(rs.getString("v")))
            }
            vertices
        }
    }

    override fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return exposedTransaction {
            var vertex: GraphVertex? = null
            exec(AgeSql.updateVertex(graphName, label, longId, properties)) { rs ->
                if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
            vertex
        }
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return exposedTransaction {
            var deleted = false
            exec(AgeSql.deleteVertex(graphName, label, longId)) { rs ->
                deleted = rs.next()
            }
            deleted
        }
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label")

        return exposedTransaction {
            var count = 0L
            exec(AgeSql.countVertices(graphName, label)) { rs ->
                if (rs.next()) {
                    count = rs.getString("count").trim().toLongOrNull() ?: 0L
                }
            }
            count
        }
    }

    // -- GraphMergeOperations --

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex {
        val properties = GraphMergeValidation.validateVertex(label, matchProperties, setProperties)

        return exposedTransaction {
            var matched: GraphVertex? = null
            exec(AgeSql.matchVertices(graphName, label, properties.matchProperties)) { rs ->
                if (rs.next()) matched = AgeTypeParser.parseVertex(rs.getString("v"))
            }
            if (matched != null) {
                if (properties.setProperties.isEmpty()) {
                    matched
                } else {
                    var updated: GraphVertex? = null
                    val id = matched.id.value.toLongOrNull()
                        ?: throw GraphQueryException("AGE requires numeric ID, got: ${matched.id.value}")
                    exec(AgeSql.updateVertex(graphName, label, id, properties.setProperties)) { rs ->
                        if (rs.next()) updated = AgeTypeParser.parseVertex(rs.getString("v"))
                    }
                    updated ?: throw GraphQueryException("Failed to update merged vertex with label=$label")
                }
            } else {
                var created: GraphVertex? = null
                exec(AgeSql.createVertex(graphName, label, properties.matchProperties + properties.setProperties)) { rs ->
                    if (rs.next()) created = AgeTypeParser.parseVertex(rs.getString("v"))
                }
                created ?: throw GraphQueryException("Failed to merge vertex with label=$label")
            }
        }
    }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge {
        val properties = GraphMergeValidation.validateEdge(fromId, toId, label, matchProperties, setProperties)
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return exposedTransaction {
            var matched: GraphEdge? = null
            val matchStmt = AgeSql.matchEdgeBetween(graphName, from, to, label, properties.matchProperties)
            exec(matchStmt) { rs ->
                if (rs.next()) matched = AgeTypeParser.parseEdge(rs.getString("e"))
            }
            if (matched != null) {
                if (properties.setProperties.isEmpty()) {
                    matched
                } else {
                    var updated: GraphEdge? = null
                    val id = matched.id.value.toLongOrNull()
                        ?: throw GraphQueryException("AGE requires numeric ID, got: ${matched.id.value}")
                    exec(AgeSql.updateEdge(graphName, label, id, properties.setProperties)) { rs ->
                        if (rs.next()) updated = AgeTypeParser.parseEdge(rs.getString("e"))
                    }
                    updated ?: throw GraphQueryException("Failed to update merged edge: $label ($fromId -> $toId)")
                }
            } else {
                var created: GraphEdge? = null
                val stmt = AgeSql.createEdge(graphName, from, to, label, properties.matchProperties + properties.setProperties)
                exec(stmt) { rs ->
                    if (rs.next()) created = AgeTypeParser.parseEdge(rs.getString("e"))
                }
                created ?: throw GraphQueryException("Failed to merge edge: $label ($fromId -> $toId)")
            }
        }
    }

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return exposedTransaction {
            var edge: GraphEdge? = null
            val stmt = AgeSql.createEdge(graphName, from, to, label, properties)
            exec(stmt) { rs ->
                if (rs.next()) {
                    edge = AgeTypeParser.parseEdge(rs.getString("e"))
                }
            }
            edge ?: throw GraphQueryException("Failed to create edge: $label ($fromId -> $toId)")
        }
    }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
        val validated = GraphBatchValidation.validateEdgeBatch(label, edges)
        if (validated.isEmpty()) return emptyList()

        val rows = validated.mapIndexed { index, edge ->
            IndexedEdge(
                index = index,
                fromId = edge.fromId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${edge.fromId.value}"),
                toId = edge.toId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${edge.toId.value}"),
                properties = edge.properties,
            )
        }

        return exposedTransaction {
            val matchedIndexes = mutableSetOf<Int>()
            rows.chunked(batchChunkSize).forEach { chunk ->
                val sqlRows = chunk.map { AgeSql.BatchEdgeRow(it.index, it.fromId, it.toId) }
                exec(AgeSql.matchBatchEdgeEndpoints(graphName, sqlRows)) { rs ->
                    while (rs.next()) {
                        matchedIndexes.add(parseBatchIndex(rs.getString("idx")))
                    }
                }
            }
            if (matchedIndexes.size != rows.size) {
                throw GraphQueryException("Failed to match all edge endpoints for label=$label")
            }

            val created = mutableListOf<Pair<Int, GraphEdge>>()
            rows.groupBy { it.properties.keys.sorted() }.values.forEach { group ->
                group.chunked(batchChunkSize).forEach { chunk ->
                    val sqlRows = chunk.map {
                        AgeSql.BatchEdgeRow(it.index, it.fromId, it.toId, it.properties)
                    }
                    exec(AgeSql.createEdgesBatch(graphName, label, sqlRows)) { rs ->
                        while (rs.next()) {
                            created.add(parseBatchIndex(rs.getString("idx")) to AgeTypeParser.parseEdge(rs.getString("e")))
                        }
                    }
                }
            }

            if (created.size != rows.size) {
                throw GraphQueryException("Failed to create all edges with label=$label")
            }
            created.sortedBy { it.first }.map { it.second }
        }
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label")
        return exposedTransaction {
            val edges = mutableListOf<GraphEdge>()
            val stmt = AgeSql.matchEdgesByLabel(graphName, label, filter)
            exec(stmt) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val longId = startId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${startId.value}")
        return exposedTransaction {
            val edges = mutableListOf<GraphEdge>()
            val stmt = AgeSql.matchEdgesByStartId(graphName, longId, edgeLabel)
            exec(stmt) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val longId = endId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${endId.value}")
        return exposedTransaction {
            val edges = mutableListOf<GraphEdge>()
            val stmt = AgeSql.matchEdgesByEndId(graphName, longId, edgeLabel)
            exec(stmt) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges
        }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return exposedTransaction {
            var deleted = false
            val stmt = AgeSql.deleteEdge(graphName, label, longId)
            exec(stmt) { rs ->
                deleted = rs.next()
            }
            deleted
        }
    }

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        val longId = startId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${startId.value}")

        return exposedTransaction {
            val vertices = mutableListOf<GraphVertex>()

            val stmt = AgeSql.neighbors(
                graphName,
                longId,
                options.edgeLabel,
                options.direction.name,
                options.maxDepth
            )
            exec(stmt) { rs ->
                while (rs.next()) {
                    vertices.add(AgeTypeParser.parseVertex(rs.getString("neighbor")))
                }
            }
            vertices
        }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        if (options.weightProperty != null) {
            return ShortestPathFallback.dijkstra(this, fromId, toId, options)
        }

        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return exposedTransaction {
            var path: GraphPath? = null
            val stmt = AgeSql.shortestPath(graphName, from, to, options.edgeLabel, options.maxDepth)
            exec(stmt) { rs ->
                if (rs.next()) {
                    path = AgeTypeParser.parsePath(rs.getString("p"))
                }
            }
            path
        }
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
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return exposedTransaction {
            val paths = mutableListOf<GraphPath>()
            val stmt = AgeSql.allPaths(graphName, from, to, options.edgeLabel, options.maxDepth)
            exec(stmt) { rs ->
                while (rs.next()) {
                    paths.add(AgeTypeParser.parsePath(rs.getString("p")))
                }
            }
            paths
        }
    }

    // -- GraphAlgorithmRepository --

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idLong = vertexId.value.toLongOrNull() ?: return DegreeResult(vertexId, 0, 0)

        var inDeg = 0
        var outDeg = 0
        exposedTransaction {
            exec(AgeSql.degreeCentrality(graphName, idLong, options.edgeLabel)) { rs ->
                if (rs.next()) {
                    inDeg = rs.getString("in_d")?.trim()?.toIntOrNull() ?: 0
                    outDeg = rs.getString("out_d")?.trim()?.toIntOrNull() ?: 0
                }
            }
        }
        return when (options.direction) {
            Direction.OUTGOING -> DegreeResult(vertexId, 0, outDeg)
            Direction.INCOMING -> DegreeResult(vertexId, inDeg, 0)
            Direction.BOTH     -> DegreeResult(vertexId, inDeg, outDeg)
        }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, options.direction)
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
        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, options.direction)
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

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.OUTGOING)
        val filteredAdjacency = if (options.vertexLabel == null) {
            adjacency
        } else {
            val allowed = vertexById.filterValues { it.label == options.vertexLabel }.keys
            adjacency.filterKeys { it in allowed }
                .mapValues { (_, dsts) -> dsts.filter { it in allowed } }
        }
        val cycles = CycleDetector.findCycles(filteredAdjacency, options.maxDepth, options.maxCycles)
        return cycles.map { ids ->
            val steps = ArrayList<PathStep>(ids.size * 2)
            ids.forEachIndexed { i, vid ->
                val gv = vertexById[vid] ?: GraphVertex(vid, options.vertexLabel ?: "", emptyMap())
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

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.BOTH)
        val filtered = if (options.vertexLabel == null) {
            vertexById
        } else {
            vertexById.filterValues { it.label == options.vertexLabel }
        }
        val ids = filtered.keys
        val uf = UnionFind(ids)
        adjacency.forEach { (s, dsts) ->
            if (s in ids) {
                dsts.forEach { d -> if (d in ids) uf.union(s, d) }
            }
        }
        return uf.groups()
            .filter { it.value.size >= options.minSize }
            .toSortedMap(compareBy { it.value })
            .map { (rep, members) ->
                GraphComponent(rep.value, members.mapNotNull { filtered[it] })
            }
    }

    override fun pageRank(options: PageRankOptions): List<PageRankScore> {
        options.vertexLabel?.requireNotBlank("vertexLabel")
        options.edgeLabel?.requireNotBlank("edgeLabel")
        log.warn { "pageRank: AGE JVM fallback in use. Use topK to limit large fetches." }

        val (adjacency, vertexById) = loadAdjacencyForFallback(options.edgeLabel, Direction.OUTGOING)
        val filtered = if (options.vertexLabel == null) {
            vertexById
        } else {
            vertexById.filterValues { it.label == options.vertexLabel }
        }
        val ids = filtered.keys
        val scores = PageRankCalculator.compute(
            vertices = ids,
            outAdjacency = adjacency.filterKeys { it in ids }.mapValues { (_, v) -> v.filter { it in ids } },
            iterations = options.iterations,
            dampingFactor = options.dampingFactor,
            tolerance = options.tolerance,
        )
        val sorted = scores.entries.sortedByDescending { it.value }
            .mapNotNull { e -> filtered[e.key]?.let { PageRankScore(it, e.value) } }
        return if (options.topK == Int.MAX_VALUE) sorted else sorted.take(options.topK)
    }

    /**
     * AGE 그래프 전체 정점 + 간선을 fetch 해 인접 리스트와 정점 맵을 만든다.
     * JVM 폴백 알고리즘에서 공통으로 사용된다.
     */
    private fun loadAdjacencyForFallback(
        edgeLabel: String?,
        direction: Direction,
    ): Pair<Map<GraphElementId, List<GraphElementId>>, Map<GraphElementId, GraphVertex>> {
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        exposedTransaction {
            // Fetch ALL vertices via MATCH (n) RETURN n
            exec(AgeSql.matchAllVertices(graphName)) { rs ->
                while (rs.next()) {
                    val v = AgeTypeParser.parseVertex(rs.getString("v"))
                    vertexById[v.id] = v
                }
            }

            // Fetch edges (labeled or all)
            val edgeSql = if (edgeLabel != null) {
                AgeSql.matchEdgesByLabel(graphName, edgeLabel, emptyMap())
            } else {
                AgeSql.matchAllEdges(graphName)
            }
            exec(edgeSql) { rs ->
                while (rs.next()) {
                    val ed = AgeTypeParser.parseEdge(rs.getString("e"))
                    if (ed.startId !in vertexById) vertexById[ed.startId] = GraphVertex(ed.startId, "", emptyMap())
                    if (ed.endId !in vertexById) vertexById[ed.endId] = GraphVertex(ed.endId, "", emptyMap())
                    when (direction) {
                        Direction.OUTGOING -> adjacency.getOrPut(ed.startId) { ArrayList() }.add(ed.endId)
                        Direction.INCOMING -> adjacency.getOrPut(ed.endId) { ArrayList() }.add(ed.startId)
                        Direction.BOTH     -> {
                            adjacency.getOrPut(ed.startId) { ArrayList() }.add(ed.endId)
                            adjacency.getOrPut(ed.endId) { ArrayList() }.add(ed.startId)
                        }
                    }
                }
            }
        }
        return adjacency to vertexById
    }

    private fun parseBatchIndex(value: String): Int =
        value.trim()
            .substringBefore("::")
            .trim()
            .toIntOrNull()
            ?: throw GraphQueryException("AGE returned non-numeric batch index: $value")

    private class AgeGraphTransactionScope(
        private val delegate: AgeGraphOperations,
    ): GraphTransactionScope,
        GraphVertexRepository by delegate,
        GraphEdgeRepository by delegate
}

private fun requirePrimaryDatabase(): Database =
    TransactionManager.primaryDatabase
        ?: error("Exposed Database가 등록되지 않았습니다. AgeGraphOperations에 명시적인 Database를 전달하세요.")

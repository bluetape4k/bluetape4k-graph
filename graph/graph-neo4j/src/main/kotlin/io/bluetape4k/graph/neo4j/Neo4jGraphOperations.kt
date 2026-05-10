package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.GraphQueryException
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
import io.bluetape4k.graph.repository.GraphMergeProperties
import io.bluetape4k.graph.repository.GraphMergeValidation
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.GraphVertexRepository
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Transaction

/**
 * Neo4j Java Driver 기반 [GraphOperations] 구현체 (동기 방식).
 *
 * blocking [Session]을 사용한다.
 *
 *
 * ```kotlin
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val ops = Neo4jGraphOperations(driver)
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 * val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * val path = ops.shortestPath(alice.id, bob.id, PathOptions(edgeLabel = "KNOWS"))
 * driver.close()
 * ```
 *
 * @param driver Neo4j Java Driver (외부 소유)
 * @param database 데이터베이스 이름 (기본: "neo4j")
 */
class Neo4jGraphOperations(
    private val driver: Driver,
    private val database: String = "neo4j",
): GraphOperations, GraphTransactionalOperations, GraphSchemaManagementOperations, GraphMergeOperations {

    companion object: KLogging()

    private fun session(): Session =
        driver.session(SessionConfig.builder().withDatabase(database).build())

    private fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> =
        session().use { s -> s.run(cypher, params).list(mapper) }

    // -- GraphSession --

    override fun createGraph(name: String) {
        name.requireNotBlank("name")
        log.debug { "Neo4j graph session initialized for database: $name" }
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
            log.warn(e) { "Neo4j service unavailable for database: $name" }
            false
        } catch (e: org.neo4j.driver.exceptions.DatabaseException) {
            log.warn(e) { "Neo4j database error checking graphExists for: $name" }
            false
        } catch (e: Exception) {
            log.warn(e) { "Unexpected error checking graphExists for: $name" }
            false
        }
    }

    override fun close() { /* driver는 외부 소유 */
    }

    override fun schemaManager(): GraphSchemaManager =
        Neo4jGraphSchemaManager(driver, database)

    // -- GraphTransactionalOperations --

    override fun <T> transaction(block: GraphTransactionScope.() -> T): T =
        session().use { session ->
            val tx = session.beginTransaction()
            var failure: Throwable? = null
            try {
                val result = block(Neo4jGraphTransactionScope(tx))
                tx.commit()
                result
            } catch (e: Throwable) {
                failure = e
                try {
                    tx.rollback()
                } catch (rollbackFailure: Throwable) {
                    e.addSuppressed(rollbackFailure)
                }
                throw e
            } finally {
                try {
                    tx.close()
                } catch (closeFailure: Throwable) {
                    failure?.addSuppressed(closeFailure) ?: throw closeFailure
                }
            }
        }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)

        return runQuery(cypher, params) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> {
        GraphBatchValidation.validateVertexBatch(label, propertiesList)
        if (propertiesList.isEmpty()) return emptyList()

        val rows = propertiesList.mapIndexed { index, properties ->
            mapOf(
                "index" to index,
                "properties" to properties,
            )
        }

        val vertices = runQuery(
            "UNWIND \$rows AS row " +
                    "CREATE (n:$label) " +
                    "SET n += row.properties " +
                    "RETURN row.index AS index, n " +
                    "ORDER BY index",
            mapOf("rows" to rows),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }

        if (vertices.size != propertiesList.size) {
            throw GraphQueryException("Failed to create all vertices: $label")
        }
        return vertices
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id RETURN n",
            mapOf("id" to id.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVertexById(id: GraphElementId): GraphVertex? {
        return runQuery(
            $$"MATCH (n) WHERE elementId(n) = $id RETURN n",
            mapOf("id" to id.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "n.$propertyKey = \$$key"
            }

        return runQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { key ->
            val propertyKey = key.requireSafeIdentifier("property key")
            "n.$propertyKey = \$$key"
        }
        val params = properties + mapOf("id" to id.value)

        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id SET $$setClause RETURN n",
            params,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return session().use { s ->
            val result = s.run(
                $$"MATCH (n:$$label) WHERE elementId(n) = $id DETACH DELETE n",
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

    // -- GraphMergeOperations --

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex {
        val properties = GraphMergeValidation.validateVertex(label, matchProperties, setProperties)
        val matchClause = mergePropertyMap("match", properties.matchProperties)
        val setClause = mergeSetClause("n", properties)
        val params = mergeParams(properties)

        return runQuery(
            $$"MERGE (n:$$label$$matchClause)$$setClause RETURN n",
            params,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to merge vertex: $label")
    }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge {
        val properties = GraphMergeValidation.validateEdge(fromId, toId, label, matchProperties, setProperties)
        val matchClause = mergePropertyMap("match", properties.matchProperties)
        val setClause = mergeSetClause("r", properties)
        val params = mergeParams(properties) + mapOf("fromId" to fromId.value, "toId" to toId.value)

        return runQuery(
            "MATCH (a), (b) WHERE elementId(a) = \$fromId AND elementId(b) = \$toId " +
                    $$"MERGE (a)-[r:$$label$$matchClause]->(b)$$setClause RETURN r",
            params,
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to merge edge: $label")
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
            $$"MATCH (a), (b) WHERE elementId(a) = $fromId AND elementId(b) = $toId " +
                    $$"CREATE (a)-[r:$$label$$propsClause]->(b) RETURN r",
            params,
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
        GraphBatchValidation.validateEdgeBatch(label, edges)
        if (edges.isEmpty()) return emptyList()

        val rows = edges.mapIndexed { index, edge ->
            mapOf(
                "index" to index,
                "fromId" to edge.fromId.value,
                "toId" to edge.toId.value,
                "properties" to edge.properties,
            )
        }

        val created = runQuery(
            "WITH \$rows AS rows, size(\$rows) AS expected " +
                    "UNWIND rows AS row " +
                    "MATCH (source), (target) " +
                    "WHERE elementId(source) = row.fromId AND elementId(target) = row.toId " +
                    "WITH collect({index: row.index, properties: row.properties, source: source, target: target}) AS matched, expected " +
                    "WHERE size(matched) = expected " +
                    "UNWIND matched AS row " +
                    "WITH row.index AS index, row.properties AS properties, row.source AS source, row.target AS target " +
                    "CREATE (source)-[r:$label]->(target) " +
                    "SET r += properties " +
                    "RETURN index, r " +
                    "ORDER BY index",
            mapOf("rows" to rows),
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }

        if (created.size != edges.size) {
            throw GraphQueryException("Failed to create all edges: $label")
        }
        return created
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "r.$propertyKey = \$$key"
            }

        return runQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) { Neo4jRecordMapper.recordToEdge(it) }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return runQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE elementId(n) = $startId RETURN r",
            mapOf("startId" to startId.value),
        ) { Neo4jRecordMapper.recordToEdge(it) }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return runQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE elementId(m) = $endId RETURN r",
            mapOf("endId" to endId.value),
        ) { Neo4jRecordMapper.recordToEdge(it) }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        id.value.requireNotBlank("id.value")

        return session().use { s ->
            val result = s.run(
                $$"MATCH ()-[r:$$label]->() WHERE elementId(r) = $id DELETE r",
                mapOf("id" to id.value)
            )
            result.consume().counters().relationshipsDeleted() > 0
        }
    }

    private fun mergePropertyMap(prefix: String, properties: Map<String, Any?>): String =
        if (properties.isEmpty()) "" else
            " {" + properties.keys.joinToString(", ") { key -> "$key: \$${prefix}_$key" } + "}"

    private fun mergeSetClause(variable: String, properties: GraphMergeProperties): String {
        if (properties.setProperties.isEmpty()) return ""
        val assignments = properties.setProperties.keys.joinToString(", ") { key ->
            "$variable.$key = \$set_$key"
        }
        return " ON CREATE SET $assignments ON MATCH SET $assignments"
    }

    private fun mergeParams(properties: GraphMergeProperties): Map<String, Any?> =
        properties.matchProperties.mapKeys { (key, _) -> "match_$key" } +
                properties.setProperties.mapKeys { (key, _) -> "set_$key" }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        startId.value.requireNotBlank("startId.value")
        val edgeLabel = options.edgeLabel?.requireNotBlank("edgeLabel")?.requireSafeIdentifier("edgeLabel")

        val depthStr = if (options.maxDepth == 1) "" else $$"*1..$${options.maxDepth}"
        val edgePart = if (edgeLabel != null) {
            ":$edgeLabel$depthStr"
        } else {
            depthStr
        }
        val pattern = when (options.direction) {
            Direction.OUTGOING -> $$"(start)-[$$edgePart]->(neighbor)"
            Direction.INCOMING -> $$"(start)<-[$$edgePart]-(neighbor)"
            Direction.BOTH     -> $$"(start)-[$$edgePart]-(neighbor)"
        }
        return runQuery(
            $$"MATCH $$pattern WHERE elementId(start) = $startId RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        fromId.value.requireNotBlank("fromId.value")
        toId.value.requireNotBlank("toId.value")

        if (options.weightProperty != null) {
            return ShortestPathFallback.dijkstra(this, fromId, toId, options)
        }

        val edgeLabel = options.edgeLabel?.requireNotBlank("edgeLabel")?.requireSafeIdentifier("edgeLabel")
        val relPattern =
            if (edgeLabel != null) ":$edgeLabel*1..${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"

        return runQuery(
            $$"MATCH p = shortestPath((a)-[$$relPattern]-(b)) " +
                    $$"WHERE elementId(a) = $fromId AND elementId(b) = $toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            Neo4jRecordMapper.recordToPath(it)
        }.firstOrNull()
    }

    override fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? {
        fromId.value.requireNotBlank("fromId.value")
        toId.value.requireNotBlank("toId.value")
        return ShortestPathFallback.aStar(this, fromId, toId, options, heuristic)
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        fromId.value.requireNotBlank("fromId.value")
        toId.value.requireNotBlank("toId.value")

        val edgeLabel = options.edgeLabel?.requireNotBlank("edgeLabel")?.requireSafeIdentifier("edgeLabel")
        val relPattern =
            if (edgeLabel != null) ":$edgeLabel*1..${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"
        
        return runQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                    $$"WHERE elementId(a) = $fromId AND elementId(b) = $toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            Neo4jRecordMapper.recordToPath(it)
        }
    }

    // -- GraphAlgorithmRepository --

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val cypher = """
            MATCH (n) WHERE elementId(n) = ${'$'}id
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

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
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
                    orderedSteps.add(PathStep.VertexStep(Neo4jRecordMapper.nodeToVertex(nodes[i])))
                    if (i < edges.size) {
                        orderedSteps.add(PathStep.EdgeStep(Neo4jRecordMapper.relationshipToEdge(edges[i])))
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

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            Neo4jRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val edges = runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            GraphElementId.of(rec["sa"].asString()) to GraphElementId.of(rec["sb"].asString())
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
        log.warn { "pageRank: Neo4j Cypher fallback in use (no GDS). Consider topK to limit results." }

        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            Neo4jRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val ids = vertexById.keys

        val outAdjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
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
        val edgePattern = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        val vertexById = HashMap<GraphElementId, GraphVertex>()
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()

        runQuery("MATCH (a)-[r$edgePattern]->(b) RETURN a, b", emptyMap<String, Any>()) { rec ->
            val av = Neo4jRecordMapper.nodeToVertex(rec["a"].asNode())
            val bv = Neo4jRecordMapper.nodeToVertex(rec["b"].asNode())
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
        val labelClause = options.vertexLabel?.let { ":${it.requireSafeIdentifier("vertexLabel")}" } ?: ""
        val edgePattern = options.edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""

        val vertices = runQuery("MATCH (n$labelClause) RETURN n", emptyMap<String, Any>()) {
            Neo4jRecordMapper.nodeToVertex(it["n"].asNode())
        }
        val vertexById = vertices.associateBy { it.id }
        val adjacency = HashMap<GraphElementId, MutableList<GraphElementId>>()
        runQuery(
            "MATCH (a$labelClause)-[r$edgePattern]->(b$labelClause) " +
                    "RETURN elementId(a) AS sa, elementId(b) AS sb",
            emptyMap<String, Any>(),
        ) { rec ->
            val s = GraphElementId.of(rec["sa"].asString())
            val e = GraphElementId.of(rec["sb"].asString())
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

private class Neo4jGraphTransactionScope(
    private val tx: Transaction,
): GraphTransactionScope {

    private fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> =
        tx.run(cypher, params).list(mapper)

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)

        return runQuery(cypher, params) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id RETURN n",
            mapOf("id" to id.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVertexById(id: GraphElementId): GraphVertex? =
        runQuery(
            $$"MATCH (n) WHERE elementId(n) = $id RETURN n",
            mapOf("id" to id.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "n.$propertyKey = \$$key"
            }

        return runQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { key ->
            val propertyKey = key.requireSafeIdentifier("property key")
            "n.$propertyKey = \$$key"
        }
        val params = properties + mapOf("id" to id.value)

        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id SET $$setClause RETURN n",
            params,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val result = tx.run(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id DETACH DELETE n",
            mapOf("id" to id.value),
        )
        return result.consume().counters().nodesDeleted() > 0
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        return tx.run($$"MATCH (n:$$label) RETURN count(n) AS cnt").single().get("cnt").asLong()
    }

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
            $$"MATCH (a), (b) WHERE elementId(a) = $fromId AND elementId(b) = $toId " +
                    $$"CREATE (a)-[r:$$label$$propsClause]->(b) RETURN r",
            params,
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "r.$propertyKey = \$$key"
            }

        return runQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return runQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE elementId(n) = $startId RETURN r",
            mapOf("startId" to startId.value),
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return runQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE elementId(m) = $endId RETURN r",
            mapOf("endId" to endId.value),
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        id.value.requireNotBlank("id.value")

        val result = tx.run(
            $$"MATCH ()-[r:$$label]->() WHERE elementId(r) = $id DELETE r",
            mapOf("id" to id.value),
        )
        return result.consume().counters().relationshipsDeleted() > 0
    }
}

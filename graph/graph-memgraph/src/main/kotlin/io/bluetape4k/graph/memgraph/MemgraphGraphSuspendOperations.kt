package io.bluetape4k.graph.memgraph

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
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphSuspendMergeOperations
import io.bluetape4k.graph.repository.GraphSuspendTransactionScope
import io.bluetape4k.graph.repository.GraphSuspendTransactionalOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManager
import io.bluetape4k.graph.schema.GraphSuspendSchemaManagementOperations
import io.bluetape4k.graph.schema.asSuspendSchemaManager
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow as asReactiveFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.reactivestreams.ReactiveSession
import org.neo4j.driver.reactivestreams.ReactiveTransaction

/**
 * Memgraph용 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * Memgraph는 Neo4j Bolt 프로토콜 + openCypher를 완전 호환하므로
 * [org.neo4j.driver.Driver]를 그대로 사용한다.
 *
 * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용한다.
 * Cypher 쿼리에서 `id(n) = toInteger($id)` 형태로 노드를 조회한다.
 *
 * [ReactiveSession] + [Flow]를 사용한다. Transactional suspend blocks run on Memgraph
 * reactive transactions, so they do not bridge through a blocking coroutine adapter.
 *
 * ```kotlin
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val ops = MemgraphGraphSuspendOperations(driver)
 *
 * suspend fun writeGraph() {
 *     val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
 *     val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25))
 *     ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 *     val count   = ops.countVertices("Person")           // 2
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, bob.id, PathOptions())
 *
 *     println(friends.map { it.properties["name"] }) // [Bob]
 * }
 * ```
 *
 * @param driver Neo4j Java Driver (외부 소유, Memgraph bolt URL로 생성)
 * @param database 데이터베이스 이름 (기본: "memgraph")
 */
class MemgraphGraphSuspendOperations(
    private val driver: Driver,
    private val database: String = "memgraph",
): GraphSuspendOperations,
   GraphSuspendTransactionalOperations,
   GraphSuspendSchemaManagementOperations,
   GraphSuspendMergeOperations {

    companion object: KLoggingChannel()

    private fun session(): ReactiveSession =
        driver.session(
            ReactiveSession::class.java,
            SessionConfig.builder().withDatabase(database).build(),
        )

    override fun schemaManager(): GraphSuspendSchemaManager =
        MemgraphGraphSchemaManager(driver, database).asSuspendSchemaManager()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T {
        val s = session()
        var failure: Throwable? = null
        try {
            val tx = beginTransaction(s) { failure = it }
            try {
                val result = MemgraphReactiveGraphSuspendTransactionScope(tx).block()
                val materializedResult = materializeTransactionResult(result)
                tx.commit<Void>().awaitFirstOrNull()
                return materializedResult
            } catch (e: Throwable) {
                failure = e
                try {
                    withContext(NonCancellable) {
                        tx.rollback<Void>().awaitFirstOrNull()
                    }
                } catch (rollbackFailure: Throwable) {
                    e.addSuppressed(rollbackFailure)
                }
                throw e
            } finally {
                failure = closeTransaction(tx, failure)
            }
        } finally {
            closeSession(s, failure)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun beginTransaction(
        session: ReactiveSession,
        onFailure: (Throwable) -> Unit,
    ): ReactiveTransaction =
        try {
            session.beginTransaction().awaitSingle()
        } catch (e: Throwable) {
            onFailure(e)
            throw e
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeTransaction(
        tx: ReactiveTransaction,
        failure: Throwable?,
    ): Throwable? =
        try {
            withContext(NonCancellable) {
                tx.close().awaitFirstOrNull()
            }
            failure
        } catch (closeFailure: Throwable) {
            if (failure != null) {
                failure.addSuppressed(closeFailure)
            } else {
                log.warn(closeFailure) { "Failed to close Memgraph reactive transaction after successful commit." }
            }
            failure
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeSession(
        session: ReactiveSession,
        failure: Throwable?,
    ) {
        try {
            withContext(NonCancellable) {
                session.close<Void>().awaitFirstOrNull()
            }
        } catch (closeFailure: Throwable) {
            if (failure != null) {
                failure.addSuppressed(closeFailure)
            } else {
                log.warn(closeFailure) { "Failed to close Memgraph reactive session after successful transaction." }
            }
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
            MemgraphGraphOperations(driver, database).mergeVertex(label, matchProperties, setProperties)
        }

    override suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        withContext(Dispatchers.IO) {
            MemgraphGraphOperations(driver, database).mergeEdge(fromId, toId, label, matchProperties, setProperties)
        }

    /**
     * 단일값/삭제 등 suspend 메서드용 쿼리 헬퍼.
     */
    private suspend fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> {
        cypher.requireNotBlank("cypher")

        val s = session()
        return try {
            val result = s.run(Query(cypher, params)).awaitSingle()
            result.records().asReactiveFlow().toList().map(mapper)
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    /**
     * 컬렉션 [Flow] 반환용 쿼리 헬퍼.
     *
     * 취소 시에도 세션이 안전하게 닫히도록 [NonCancellable]을 사용한다.
     */
    private fun <T> flowQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): Flow<T> {
        cypher.requireNotBlank("cypher")

        return flow {
            val s = session()
            try {
                val result = s.run(Query(cypher, params)).awaitSingle()
                emitAll(result.records().asReactiveFlow().map(mapper))
            } finally {
                withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
            }
        }
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) {
        name.requireNotBlank("name")
        log.debug { "Memgraph graph session initialized for database: $name" }
    }

    override suspend fun dropGraph(name: String) {
        name.requireNotBlank("name")
        runQuery("MATCH (n) DETACH DELETE n") { it }
    }

    override suspend fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")

        var s: ReactiveSession? = null
        return try {
            s = session()
            val result = s.run(Query("RETURN 1")).awaitSingle()
            result.records().awaitFirstOrNull() != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: org.neo4j.driver.exceptions.DatabaseException) {
            if (e.isMissingDatabaseFailure()) false else throw e.asGraphExistsFailure("Memgraph", name)
        } catch (e: Exception) {
            throw e.asGraphExistsFailure("Memgraph", name)
        } finally {
            s?.let { session ->
                withContext(NonCancellable) { session.close<Void>().awaitFirstOrNull() }
            }
        }
    }

    override fun close() { /* driver는 외부 소유 */
    }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)

        return runQuery(cypher, params) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override suspend fun createVertices(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<GraphVertex> =
        withContext(Dispatchers.IO) {
            MemgraphGraphOperations(driver, database).createVertices(label, propertiesList)
        }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? {
        return runQuery(
            "MATCH (n) WHERE id(n) = toInteger(\$id) RETURN n",
            mapOf("id" to id.value),
        ) { MemgraphRecordMapper.recordToVertex(it) }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "n.$propertyKey = \$$key"
            }

        return flowQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { key ->
            val propertyKey = key.requireSafeIdentifier("property key")
            "n.$propertyKey = \$$key"
        }
        val params = properties + mapOf("id" to id.value)

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) { MemgraphRecordMapper.recordToVertex(it) }.firstOrNull()
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val s = session()

        return try {
            val result = s.run(
                Query($$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().nodesDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val s = session()
        return try {
            val result = s.run(Query($$"MATCH (n:$$label) RETURN count(n) AS cnt")).awaitSingle()
            result.records().awaitFirstOrNull()?.get("cnt")?.asLong() ?: 0L
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendEdgeRepository --

    override suspend fun createEdge(
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

    override suspend fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        withContext(Dispatchers.IO) {
            MemgraphGraphOperations(driver, database).createEdges(label, edges)
        }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val whereClause =
            if (filter.isEmpty()) ""
            else " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "r.$propertyKey = \$$key"
            }

        return flowQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return flowQuery(
            "MATCH (n)-[r$labelPart]->(m) WHERE id(n) = toInteger(\$startId) RETURN r",
            mapOf("startId" to startId.value),
        ) { MemgraphRecordMapper.recordToEdge(it) }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return flowQuery(
            "MATCH (n)-[r$labelPart]->(m) WHERE id(m) = toInteger(\$endId) RETURN r",
            mapOf("endId" to endId.value),
        ) { MemgraphRecordMapper.recordToEdge(it) }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val s = session()

        return try {
            val result = s.run(
                Query($$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().relationshipsDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): Flow<GraphVertex> {
        startId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $startId")
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

        return flowQuery(
            $$"MATCH $$pattern WHERE id(start) = toInteger($startId) RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        fromId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $fromId")
        toId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $toId")

        if (options.weightProperty != null) {
            return withContext(Dispatchers.IO) { ShortestPathFallback.dijkstra(syncDelegate, fromId, toId, options) }
        }

        // Memgraph는 shortestPath() 미지원 → depth-limited MATCH + ORDER BY length(p) LIMIT 1 사용
        val edgeLabel = options.edgeLabel?.requireNotBlank("edgeLabel")?.requireSafeIdentifier("edgeLabel")
        val relPattern =
            if (edgeLabel != null) ":$edgeLabel*1..${options.maxDepth}"
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

    override suspend fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? {
        fromId.value.requireNotBlank("fromId.value")
        toId.value.requireNotBlank("toId.value")
        return withContext(Dispatchers.IO) { ShortestPathFallback.aStar(syncDelegate, fromId, toId, options, heuristic) }
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> {
        fromId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $fromId")
        toId.value.toLongOrNull() ?: throw GraphQueryException("Memgraph requires numeric ID, got: $toId")

        val edgeLabel = options.edgeLabel?.requireNotBlank("edgeLabel")?.requireSafeIdentifier("edgeLabel")
        val relPattern =
            if (edgeLabel != null) ":$edgeLabel*1..${options.maxDepth}"
            else $$"*1..$${options.maxDepth}"
        return flowQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                    $$"WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            MemgraphRecordMapper.recordToPath(it)
        }
    }

    // -- GraphSuspendAlgorithmRepository --

    private val syncDelegate by lazy { MemgraphGraphOperations(driver, database) }

    override fun pageRank(options: PageRankOptions): Flow<PageRankScore> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.pageRank(options) }
        list.forEach { emit(it) }
    }

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = withContext(Dispatchers.IO) {
        syncDelegate.degreeCentrality(vertexId, options)
    }

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.connectedComponents(options) }
        list.forEach { emit(it) }
    }

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.bfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): Flow<TraversalVisit> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.dfs(startId, options) }
        list.forEach { emit(it) }
    }

    override fun detectCycles(options: CycleOptions): Flow<GraphCycle> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.detectCycles(options) }
        list.forEach { emit(it) }
    }
}

@Suppress("TooManyFunctions")
private class MemgraphReactiveGraphSuspendTransactionScope(
    private val tx: ReactiveTransaction,
): GraphSuspendTransactionScope {

    private suspend fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> {
        val result = tx.run(Query(cypher, params)).awaitSingle()
        return result.records().asReactiveFlow().toList().map(mapper)
    }

    private fun <T> flowQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): Flow<T> = flow {
        val result = tx.run(Query(cypher, params)).awaitSingle()
        emitAll(result.records().asReactiveFlow().map(mapper))
    }

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)

        return runQuery(cypher, params) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? =
        runQuery(
            "MATCH (n) WHERE id(n) = toInteger(\$id) RETURN n",
            mapOf("id" to id.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "n.$propertyKey = \$$key"
            }

        return flowQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { key ->
            val propertyKey = key.requireSafeIdentifier("property key")
            "n.$propertyKey = \$$key"
        }
        val params = properties + mapOf("id" to id.value)

        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")

        val result = tx.run(
            Query(
                $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n",
                mapOf("id" to id.value),
            ),
        ).awaitSingle()
        return result.consume().awaitSingle().counters().nodesDeleted() > 0
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val result = tx.run(Query($$"MATCH (n:$$label) RETURN count(n) AS cnt")).awaitSingle()
        return result.records().awaitFirstOrNull()?.get("cnt")?.asLong() ?: 0L
    }

    override suspend fun createEdge(
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

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { key ->
                val propertyKey = key.requireSafeIdentifier("property key")
                "r.$propertyKey = \$$key"
            }

        return flowQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return flowQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE id(n) = toInteger($startId) RETURN r",
            mapOf("startId" to startId.value),
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val labelPart = edgeLabel?.let { ":${it.requireSafeIdentifier("edgeLabel")}" } ?: ""
        return flowQuery(
            $$"MATCH (n)-[r$$labelPart]->(m) WHERE id(m) = toInteger($endId) RETURN r",
            mapOf("endId" to endId.value),
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        id.value.requireNotBlank("id.value")

        val result = tx.run(
            Query(
                $$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r",
                mapOf("id" to id.value),
            ),
        ).awaitSingle()
        return result.consume().awaitSingle().counters().relationshipsDeleted() > 0
    }
}

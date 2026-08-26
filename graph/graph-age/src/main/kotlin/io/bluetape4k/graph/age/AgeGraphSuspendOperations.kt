package io.bluetape4k.graph.age

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.algo.ShortestPathFallback
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
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
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphSuspendTransactionScope
import io.bluetape4k.graph.repository.GraphSuspendTransactionalOperations
import io.bluetape4k.graph.repository.materializeSuspendTransactionResult
import io.bluetape4k.graph.schema.GraphSuspendSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManager
import io.bluetape4k.graph.schema.asSuspendSchemaManager
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction as exposedSuspendTransaction
import java.sql.ResultSet
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_STREAM_FETCH_SIZE = 100

/**
 * Apache AGE + PostgreSQL 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * **AGE 초기화 전략:**
 * - `CREATE EXTENSION IF NOT EXISTS age`: PostgreSQLAgeServer 시작 시 1회 실행 (DB 수준)
 * - 매 connection: `LOAD 'age'` + `SET search_path` 는 HikariCP connectionInitSql로 처리
 *
 * **Dispatcher 경계:**
 * - 단일값 반환 `suspend fun`은 내부적으로 [Dispatchers.IO]에서 실행
 * - 직접 조회 `Flow<T>`는 [Dispatchers.IO]의 JDBC cursor를 행 단위로 읽고 channel
 *   backpressure를 적용한다.
 * - `suspendTransaction { ... }` 안의 Flow는 commit 전에 materialize한다. 트랜잭션
 *   소유권이 끝난 뒤 cursor를 노출하지 않는 별도 계약이다.
 *
 * ```kotlin
 * // HikariCP DataSource 생성 (connectionInitSql로 AGE 로드)
 * val database = Database.connect(dataSource)
 * val ops = AgeGraphSuspendOperations(database, "social")
 *
 * suspend fun writeGraph() {
 *     ops.createGraph("social")
 *
 *     val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
 *     val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25))
 *     ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, bob.id, PathOptions())
 *
 *     ops.dropGraph("social")
 * }
 * ```
 *
 * @param database 이 facade가 사용할 Exposed 데이터베이스
 * @param graphName AGE 그래프 이름
 */
@Suppress("DEPRECATION")
class AgeGraphSuspendOperations(
    private val database: Database,
    private val graphName: String,
): GraphSuspendOperations,
   GraphSuspendTransactionalOperations,
   GraphSuspendSchemaManagementOperations,
   GraphSuspendMergeOperations {

    /**
     * 전역 Exposed Database를 사용하는 기존 생성자다.
     *
     * 새 코드는 여러 DataSource를 안전하게 격리할 수 있도록 [Database]를 명시해야 한다.
     */
    @Deprecated("명시적인 Database를 전달하는 생성자를 사용하세요.")
    constructor(graphName: String): this(requirePrimaryDatabase(), graphName)

    companion object: KLoggingChannel()

    init {
        graphName.requireNotBlank("graphName").requireSafeIdentifier("graphName")
    }

    private suspend fun <T> newSuspendedTransaction(
        context: CoroutineContext = Dispatchers.IO,
        attempts: Int? = null,
        statement: suspend JdbcTransaction.() -> T,
    ): T = withContext(context) {
        exposedSuspendTransaction(db = database) {
            attempts?.let { maxAttempts = it }
            statement()
        }
    }

    /**
     * Exposed의 문자열 [exec]는 fetch size를 조정할 수 없으므로
     * [BlockingExecutable] 경계에서 PostgreSQL cursor fetch size를 설정한다.
     * 이 경계를 통해 pgjdbc 기본 fetch-all 동작을 피하고 transaction이 소유한
     * [ResultSet]과 prepared statement lifecycle을 그대로 유지한다.
     */
    private fun JdbcTransaction.execStreaming(
        statementText: String,
        transform: (ResultSet) -> Unit,
    ) {
        val executable = object : BlockingExecutable<Unit, Statement<Unit>> {
            override val statement = object : Statement<Unit>(StatementType.SELECT, emptyList()) {
                override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = statementText

                override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> =
                    listOf(emptyList())
            }

            override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Unit? {
                fetchSize = transaction.db.defaultFetchSize?.takeIf { it > 0 } ?: DEFAULT_STREAM_FETCH_SIZE
                executeQuery().result.use(transform)
                return Unit
            }
        }

        exec(executable)
    }

    /**
     * Exposed의 callback 기반 JDBC 조회를 lazy, bounded [Flow]로 변환한다.
     *
     * Exposed callback은 suspend 함수가 아니므로 [trySendBlocking]을 사용한다.
     * 이 bridge는 [Dispatchers.IO]에서만 실행하며, collector가 느리면 channel이
     * backpressure를 적용하고 collector가 취소되면 callback과 transaction이
     * 종료되어 [ResultSet]이 닫힌다. JDBC prepared statement에는 positive fetch
     * size를 적용하고 streaming transaction retry를 비활성화해 driver fetch-all과
     * retry 시 prefix 중복 방출을 막는다.
     */
    private fun <T> streamQuery(
        statement: String,
        mapper: (ResultSet) -> T,
    ): Flow<T> = channelFlow {
        val output = this@channelFlow
        newSuspendedTransaction(Dispatchers.IO, attempts = 1) {
            execStreaming(statement) { resultSet ->
                while (resultSet.next()) {
                    output.trySendBlocking(mapper(resultSet)).getOrThrow()
                }
            }
        }
    }

    override fun schemaManager(): GraphSuspendSchemaManager =
        AgeGraphSchemaManager().asSuspendSchemaManager()

    override suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            val result = AgeGraphSuspendTransactionScope(graphName).block()
            materializeSuspendTransactionResult(result)
        }

    override suspend fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        withContext(Dispatchers.IO) {
            AgeGraphOperations(database, graphName).mergeVertex(label, matchProperties, setProperties)
        }

    override suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        withContext(Dispatchers.IO) {
            AgeGraphOperations(database, graphName).mergeEdge(fromId, toId, label, matchProperties, setProperties)
        }

    override suspend fun createGraph(name: String) {
        name.requireNotBlank("name")

        newSuspendedTransaction {
            try {
                exec(AgeSql.createGraph(name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isDuplicateGraphFailure()) {
                    log.debug("Graph '$name' already exists: ${e.message}")
                } else {
                    throw e.asCreateGraphFailure(name)
                }
            }
        }
    }

    override suspend fun dropGraph(name: String) {
        name.requireNotBlank("name")

        newSuspendedTransaction {
            exec(AgeSql.dropGraph(name))
        }
    }

    override suspend fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")

        return newSuspendedTransaction {
            var count = 0L
            val stmt = AgeSql.graphExists(name)
            exec(stmt) { rs ->
                if (rs.next()) {
                    count = rs.getLong(1)
                }
            }
            count > 0
        }
    }

    override fun close() {
        // database는 외부 소유이므로 닫지 않음
    }

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")
        return newSuspendedTransaction {
            var vertex: GraphVertex? = null
            val stmt = AgeSql.createVertex(graphName, label, properties)
            exec(stmt) { rs ->
                if (rs.next()) {
                    vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
            }
            vertex ?: throw GraphQueryException("Failed to create vertex with label=$label")
        }
    }

    override suspend fun createVertices(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<GraphVertex> =
        withContext(Dispatchers.IO) {
            AgeGraphOperations(database, graphName).createVertices(label, propertiesList)
        }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            var vertex: GraphVertex? = null
            val stmt = AgeSql.matchVertexById(graphName, label, longId)
            exec(stmt) { rs ->
                if (rs.next()) {
                    vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
            }
            vertex
        }
    }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? {
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            var vertex: GraphVertex? = null
            val stmt = AgeSql.matchVertexById(graphName, longId)
            exec(stmt) { rs ->
                if (rs.next()) {
                    vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
            }
            vertex
        }
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label")
        return streamQuery(AgeSql.matchVertices(graphName, label, filter)) { resultSet ->
            AgeTypeParser.parseVertex(resultSet.getString("v"))
        }
    }

    override suspend fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull() ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            var vertex: GraphVertex? = null
            val stmt = AgeSql.updateVertex(graphName, label, longId, properties)
            exec(stmt) { rs ->
                if (rs.next()) {
                    vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
            }
            vertex
        }
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId =
            id.value.toLongOrNull() ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            var deleted = false
            val stmt = AgeSql.deleteVertex(graphName, label, longId)
            exec(stmt) { rs ->
                deleted = rs.next()
            }
            deleted
        }
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label")
        return newSuspendedTransaction {
            var count = 0L
            val stmt = AgeSql.countVertices(graphName, label)
            exec(stmt) { rs ->
                if (rs.next()) {
                    count = rs.getString("count").trim().toLongOrNull() ?: 0L
                }
            }
            count
        }
    }

    override suspend fun createEdge(
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

        return newSuspendedTransaction {
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

    override suspend fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        withContext(Dispatchers.IO) {
            AgeGraphOperations(database, graphName).createEdges(label, edges)
        }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label")
        return streamQuery(AgeSql.matchEdgesByLabel(graphName, label, filter)) { resultSet ->
            AgeTypeParser.parseEdge(resultSet.getString("e"))
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val longId = startId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${startId.value}")
        return streamQuery(AgeSql.matchEdgesByStartId(graphName, longId, edgeLabel)) { resultSet ->
            AgeTypeParser.parseEdge(resultSet.getString("e"))
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val longId = endId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${endId.value}")
        return streamQuery(AgeSql.matchEdgesByEndId(graphName, longId, edgeLabel)) { resultSet ->
            AgeTypeParser.parseEdge(resultSet.getString("e"))
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
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
    ): Flow<GraphVertex> {
        val longId = startId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${startId.value}")

        return streamQuery(
            AgeSql.neighbors(graphName, longId, options.edgeLabel, options.direction.name, options.maxDepth)
        ) { resultSet ->
            AgeTypeParser.parseVertex(resultSet.getString("neighbor"))
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        if (options.weightProperty != null) {
            return withContext(Dispatchers.IO) {
                ShortestPathFallback.dijkstra(syncDelegate, fromId, toId, options)
            }
        }

        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to =
            toId.value.toLongOrNull() ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return newSuspendedTransaction {
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

    override suspend fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? = withContext(Dispatchers.IO) {
        syncDelegate.aStarPath(fromId, toId, options, heuristic)
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> {
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return streamQuery(AgeSql.allPaths(graphName, from, to, options.edgeLabel, options.maxDepth)) { resultSet ->
            AgeTypeParser.parsePath(resultSet.getString("p"))
        }
    }

    // -- GraphSuspendAlgorithmRepository --

    private val syncDelegate by lazy { AgeGraphOperations(database, graphName) }

    override suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = withContext(Dispatchers.IO) {
        syncDelegate.degreeCentrality(vertexId, options)
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

    override fun connectedComponents(options: ComponentOptions): Flow<GraphComponent> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.connectedComponents(options) }
        list.forEach { emit(it) }
    }

    override fun pageRank(options: PageRankOptions): Flow<PageRankScore> = flow {
        val list = withContext(Dispatchers.IO) { syncDelegate.pageRank(options) }
        list.forEach { emit(it) }
    }
}

@Suppress("TooManyFunctions")
private class AgeGraphSuspendTransactionScope(
    private val graphName: String,
): GraphSuspendTransactionScope {

    private fun GraphElementId.toAgeLong(): Long =
        value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: $value")

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")

        var vertex: GraphVertex? = null
        TransactionManager.current().exec(AgeSql.createVertex(graphName, label, properties)) { rs ->
            if (rs.next()) {
                vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
        }
        return vertex ?: throw GraphQueryException("Failed to create vertex with label=$label")
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.toAgeLong()

        var vertex: GraphVertex? = null
        TransactionManager.current().exec(AgeSql.matchVertexById(graphName, label, longId)) { rs ->
            if (rs.next()) {
                vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
        }
        return vertex
    }

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? {
        val longId = id.toAgeLong()

        var vertex: GraphVertex? = null
        TransactionManager.current().exec(AgeSql.matchVertexById(graphName, longId)) { rs ->
            if (rs.next()) {
                vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
        }
        return vertex
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label")
        return flow {
            val vertices = mutableListOf<GraphVertex>()
            TransactionManager.current().exec(AgeSql.matchVertices(graphName, label, filter)) { rs ->
                while (rs.next()) {
                    vertices.add(AgeTypeParser.parseVertex(rs.getString("v")))
                }
            }
            vertices.forEach { emit(it) }
        }
    }

    override suspend fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.toAgeLong()

        var vertex: GraphVertex? = null
        TransactionManager.current().exec(AgeSql.updateVertex(graphName, label, longId, properties)) { rs ->
            if (rs.next()) {
                vertex = AgeTypeParser.parseVertex(rs.getString("v"))
            }
        }
        return vertex
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.toAgeLong()

        var deleted = false
        TransactionManager.current().exec(AgeSql.deleteVertex(graphName, label, longId)) { rs ->
            deleted = rs.next()
        }
        return deleted
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label")

        var count = 0L
        TransactionManager.current().exec(AgeSql.countVertices(graphName, label)) { rs ->
            if (rs.next()) {
                count = rs.getString("count").trim().toLongOrNull() ?: 0L
            }
        }
        return count
    }

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val from = fromId.toAgeLong()
        val to = toId.toAgeLong()

        var edge: GraphEdge? = null
        TransactionManager.current().exec(AgeSql.createEdge(graphName, from, to, label, properties)) { rs ->
            if (rs.next()) {
                edge = AgeTypeParser.parseEdge(rs.getString("e"))
            }
        }
        return edge ?: throw GraphQueryException("Failed to create edge: $label ($fromId -> $toId)")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label")
        return flow {
            val edges = mutableListOf<GraphEdge>()
            TransactionManager.current().exec(AgeSql.matchEdgesByLabel(graphName, label, filter)) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges.forEach { emit(it) }
        }
    }

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val longId = startId.toAgeLong()

        return flow {
            val edges = mutableListOf<GraphEdge>()
            TransactionManager.current().exec(AgeSql.matchEdgesByStartId(graphName, longId, edgeLabel)) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges.forEach { emit(it) }
        }
    }

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> {
        val longId = endId.toAgeLong()

        return flow {
            val edges = mutableListOf<GraphEdge>()
            TransactionManager.current().exec(AgeSql.matchEdgesByEndId(graphName, longId, edgeLabel)) { rs ->
                while (rs.next()) {
                    edges.add(AgeTypeParser.parseEdge(rs.getString("e")))
                }
            }
            edges.forEach { emit(it) }
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.toAgeLong()

        var deleted = false
        TransactionManager.current().exec(AgeSql.deleteEdge(graphName, label, longId)) { rs ->
            deleted = rs.next()
        }
        return deleted
    }
}

private fun requirePrimaryDatabase(): Database =
    TransactionManager.primaryDatabase
        ?: error("Exposed Database가 등록되지 않았습니다. AgeGraphSuspendOperations에 명시적인 Database를 전달하세요.")

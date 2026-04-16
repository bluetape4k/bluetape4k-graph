package io.bluetape4k.graph.age

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
import io.bluetape4k.graph.model.BfsDfsOptions
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
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * Apache AGE + PostgreSQL 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * **AGE 초기화 전략:**
 * - `CREATE EXTENSION IF NOT EXISTS age`: PostgreSQLAgeServer 시작 시 1회 실행 (DB 수준)
 * - 매 connection: `LOAD 'age'` + `SET search_path` 는 HikariCP connectionInitSql로 처리
 *
 * **Dispatcher 경계:**
 * - 단일값 반환 `suspend fun`은 내부적으로 [Dispatchers.IO]에서 실행
 * - 컬렉션 반환 `fun ... : Flow<T>`는 `flow { }` 빌더 안에서 [Dispatchers.IO]로 감싸 실행
 *
 * ```kotlin
 * // HikariCP DataSource 생성 (connectionInitSql로 AGE 로드)
 * val ops = AgeGraphSuspendOperations("social")
 *
 * runBlocking {
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
 * @param graphName AGE 그래프 이름
 */
@Suppress("DEPRECATION")
class AgeGraphSuspendOperations(
    private val graphName: String,
): GraphSuspendOperations {

    companion object: KLoggingChannel()

    init {
        graphName.requireNotBlank("graphName")
    }

    override suspend fun createGraph(name: String) {
        name.requireNotBlank("name")

        newSuspendedTransaction {
            loadAgeAndSetSearchPath()
            try {
                exec(AgeSql.createGraph(name))
            } catch (e: Exception) {
                // 이미 존재하는 경우 무시
                log.debug("Graph '$name' may already exist: ${e.message}")
            }
        }
    }

    override suspend fun dropGraph(name: String) {
        name.requireNotBlank("name")

        newSuspendedTransaction {
            loadAgeAndSetSearchPath()
            exec(AgeSql.dropGraph(name))
        }
    }

    override suspend fun graphExists(name: String): Boolean {
        name.requireNotBlank("name")

        return newSuspendedTransaction {
            loadAgeAndSetSearchPath()
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
            loadAgeAndSetSearchPath()

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

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            loadAgeAndSetSearchPath()

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

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label")
        return channelFlow {
            val vertices = newSuspendedTransaction {
                loadAgeAndSetSearchPath()
                val list = mutableListOf<GraphVertex>()
                val stmt = AgeSql.matchVertices(graphName, label, filter)
                exec(stmt) { rs ->
                    while (rs.next()) {
                        list.add(AgeTypeParser.parseVertex(rs.getString("v")))
                    }
                }
                list
            }
            vertices.forEach { send(it) }
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
            loadAgeAndSetSearchPath()

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
            loadAgeAndSetSearchPath()

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
            loadAgeAndSetSearchPath()
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
            loadAgeAndSetSearchPath()

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

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label")
        return channelFlow {
            val edges = newSuspendedTransaction {
                loadAgeAndSetSearchPath()
                val list = mutableListOf<GraphEdge>()
                val stmt = AgeSql.matchEdgesByLabel(graphName, label, filter)
                exec(stmt) { rs ->
                    while (rs.next()) {
                        list.add(AgeTypeParser.parseEdge(rs.getString("e")))
                    }
                }
                list
            }
            edges.forEach { send(it) }
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val longId = id.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")

        return newSuspendedTransaction {
            loadAgeAndSetSearchPath()

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

        return channelFlow {
            val vertices = newSuspendedTransaction {
                loadAgeAndSetSearchPath()

                val list = mutableListOf<GraphVertex>()
                val stmt = AgeSql.neighbors(
                    graphName, longId, options.edgeLabel, options.direction.name, options.maxDepth
                )
                exec(stmt) { rs ->
                    while (rs.next()) {
                        list.add(AgeTypeParser.parseVertex(rs.getString("neighbor")))
                    }
                }
                list
            }
            vertices.forEach { send(it) }
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to =
            toId.value.toLongOrNull() ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return newSuspendedTransaction {
            loadAgeAndSetSearchPath()
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

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> {
        val from = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
        val to = toId.value.toLongOrNull()
            ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")

        return channelFlow {
            val paths = newSuspendedTransaction {
                loadAgeAndSetSearchPath()
                val list = mutableListOf<GraphPath>()
                val stmt = AgeSql.allPaths(graphName, from, to, options.edgeLabel, options.maxDepth)
                exec(stmt) { rs ->
                    while (rs.next()) {
                        list.add(AgeTypeParser.parsePath(rs.getString("p")))
                    }
                }
                list
            }
            paths.forEach { send(it) }
        }
    }

    // -- GraphSuspendAlgorithmRepository --

    private val syncDelegate by lazy { AgeGraphOperations(graphName) }

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

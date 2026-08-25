package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.suspendTransaction
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

@TestMethodOrder(OrderAnnotation::class)
class AgeGraphSuspendOperationsTest {

    companion object: KLogging()

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var ops: AgeGraphSuspendOperations

    private val graphName = "test_graph"

    @BeforeAll
    fun setup() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        database = Database.connect(
            dataSource,
            databaseConfig = DatabaseConfig {
                defaultFetchSize = 8
            }
        )
        ops = AgeGraphSuspendOperations(database, graphName)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    @BeforeEach
    fun resetGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        ops.createGraph(graphName)
    }

    // ───────────────────────── 그래프 생성/삭제/존재 여부 ─────────────────────────

    @Test
    @Order(10)
    fun `그래프를 생성하면 존재 여부가 true 반환`() = runSuspendIO {
        ops.graphExists(graphName).shouldBeTrue()
    }

    @Test
    @Order(11)
    fun `이미 존재하는 그래프 생성은 호환성 duplicate로 허용한다`() = runSuspendIO {
        ops.createGraph(graphName)

        ops.graphExists(graphName).shouldBeTrue()
    }

    @Test
    @Order(12)
    fun `그래프를 삭제하면 존재 여부가 false 반환`() = runSuspendIO {
        ops.dropGraph(graphName)
        ops.graphExists(graphName).shouldBeFalse()
    }

    // ───────────────────────── 정점(Vertex) CRUD ─────────────────────────

    @Test
    @Order(20)
    fun `정점을 생성하면 id가 부여된다`() = runSuspendIO {
        val vertex = ops.createVertex("Person")
        vertex.id.shouldNotBeNull()
        vertex.id.value.shouldNotBeNull()
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() = runSuspendIO {
        val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        vertex.id.shouldNotBeNull()
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `id로 정점을 조회한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)
        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() = runSuspendIO {
        val fakeId = GraphElementId.of(999999999L)
        val found = ops.findVertexById("Person", fakeId)
        found.shouldBeNull()
    }

    @Test
    @Order(24)
    fun `label로 정점 목록을 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        val vertices = ops.findVerticesByLabel("Person").toList()
        vertices.shouldNotBeEmpty()
        vertices.size shouldBeGreaterThan 1
    }

    @Test
    @Order(25)
    fun `filter 조건으로 정점을 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        ops.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))
        val vertices = ops.findVerticesByLabel("Person", mapOf("city" to "Seoul")).toList()
        vertices.shouldNotBeEmpty()
        vertices.all { it.properties["city"] == "Seoul" }.shouldBeTrue()
    }

    @Test
    @Order(26)
    fun `정점의 properties를 업데이트한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 25))
        val updated = ops.updateVertex("Person", created.id, mapOf("name" to "Alice", "age" to 26))
        updated.shouldNotBeNull()
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(27)
    fun `정점을 삭제한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "ToDelete"))
        val deleted = ops.deleteVertex("Person", created.id)
        deleted.shouldBeTrue()
        val found = ops.findVertexById("Person", created.id)
        found.shouldBeNull()
    }

    @Test
    @Order(28)
    fun `정점 개수를 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("Person", mapOf("name" to "Carol"))
        val count = ops.countVertices("Person")
        count shouldBeEqualTo 3L
    }

    @Test
    @Order(29)
    fun `suspendTransaction은 실패 시 생성한 정점과 간선을 rollback한다`() = runSuspendIO {
        val existing = ops.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<IllegalStateException> {
            ops.suspendTransaction {
                val alice = createVertex("Person", mapOf("name" to "Alice"))
                val bob = createVertex("Person", mapOf("name" to "Bob"))
                createEdge(alice.id, bob.id, "KNOWS")
                error("rollback")
            }
        }

        ops.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        ops.countVertices("Person") shouldBeEqualTo 1L
        ops.findEdgesByLabel("KNOWS").toList().shouldBeEmpty()
    }

    @Test
    @Order(30)
    fun `suspendTransaction은 취소 시 빠르게 반환하고 rollback한다`() = runSuspendIO {
        val existing = ops.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) {
                ops.suspendTransaction {
                    createVertex("Person", mapOf("name" to "Cancelled"))
                    awaitCancellation()
                }
            }
        }

        ops.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        ops.countVertices("Person") shouldBeEqualTo 1L
    }

    @Test
    @Order(31)
    fun `suspendTransaction은 scoped vertex와 edge CRUD를 지원한다`() = runSuspendIO {
        val result = ops.suspendTransaction {
            val alice = createVertex("Person", mapOf("name" to "Alice"))
            val bob = createVertex("Person", mapOf("name" to "Bob"))

            findVertexById("Person", alice.id)?.properties?.get("name") shouldBeEqualTo "Alice"
            findVertexById(bob.id)?.properties?.get("name") shouldBeEqualTo "Bob"
            countVertices("Person") shouldBeEqualTo 2L

            updateVertex("Person", alice.id, mapOf("age" to 30L))?.properties?.get("age") shouldBeEqualTo 30L

            val edge = createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2026L))
            findEdgesByLabel("KNOWS", mapOf("since" to 2026L)).toList().shouldNotBeEmpty()
            findEdgesByStartId(alice.id, "KNOWS").toList().shouldNotBeEmpty()
            findEdgesByEndId(bob.id).toList().shouldNotBeEmpty()

            deleteEdge("KNOWS", edge.id).shouldBeTrue()
            deleteVertex("Person", bob.id).shouldBeTrue()

            countVertices("Person")
        }

        result shouldBeEqualTo 1L
        ops.countVertices("Person") shouldBeEqualTo 1L
    }

    @Test
    @Order(32)
    fun `suspendTransaction은 반환된 Flow를 commit 전에 materialize한다`() = runSuspendIO {
        val people = ops.suspendTransaction {
            createVertex("Person", mapOf("name" to "Alice"))
            findVerticesByLabel("Person")
        }

        val names = people.toList().map { it.properties["name"] }
        names.any { it == "Alice" }.shouldBeTrue()
    }

    @Test
    @Order(32_1)
    fun `직접 조회 Flow를 조기 취소해도 JDBC 자원을 반환한다`() = runSuspendIO {
        ops.suspendTransaction {
            repeat(256) { index ->
                createVertex("Person", mapOf("name" to "Person-$index"))
            }
        }

        withTimeout(5_000) {
            repeat(8) {
                ops.findVerticesByLabel("Person").first()
            }
        }

        ops.countVertices("Person") shouldBeEqualTo 256L
    }

    @Test
    @Order(32_2)
    fun `직접 조회 Flow의 collector 예외가 전파되어도 JDBC 자원을 반환한다`() = runSuspendIO {
        ops.suspendTransaction {
            repeat(128) { index ->
                createVertex("Person", mapOf("name" to "Person-$index"))
            }
        }

        assertFailsWith<IllegalStateException> {
            ops.findVerticesByLabel("Person").collect {
                error("collector failure")
            }
        }

        ops.countVertices("Person") shouldBeEqualTo 128L
    }

    @Test
    @Order(32_3)
    fun `직접 조회 Flow는 DatabaseConfig fetch size를 PreparedStatement에 전달한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "fetch-size"))

        val probe = StreamingProbe()
        val probedOps = probedOperations(defaultFetchSize = 8, probe)

        probedOps.findVerticesByLabel("Person").toList().shouldNotBeEmpty()

        probe.fetchSizes.any { it == 8 }.shouldBeTrue()
    }

    @Test
    @Order(32_4)
    fun `직접 조회 Flow는 비양수 DatabaseConfig에서 positive 기본 fetch size를 사용한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "fallback-fetch-size"))

        val probe = StreamingProbe()
        val probedOps = probedOperations(defaultFetchSize = 0, probe)

        probedOps.findVerticesByLabel("Person").toList().shouldNotBeEmpty()

        probe.fetchSizes.any { it == 100 }.shouldBeTrue()
    }

    @Test
    @Order(32_5)
    fun `늦은 JDBC 오류는 prefix 중복 없이 streaming transaction 한 번으로 종료된다`() = runSuspendIO {
        repeat(2) { index ->
            ops.createVertex("Person", mapOf("name" to "late-failure-$index"))
        }

        val probe = StreamingProbe(failAfterFirstRow = true)
        val probedOps = probedOperations(defaultFetchSize = 8, probe)
        val emitted = mutableListOf<String>()

        val failure = assertFailsWith<Exception> {
            probedOps.findVerticesByLabel("Person").collect { vertex ->
                emitted += vertex.properties["name"].toString()
            }
        }

        generateSequence<Throwable>(failure) { it.cause }.any { it is SQLException }.shouldBeTrue()
        emitted.size shouldBeEqualTo 1
        probe.streamingAttempts.get() shouldBeEqualTo 1
    }

    private fun probedOperations(defaultFetchSize: Int, probe: StreamingProbe): AgeGraphSuspendOperations =
        AgeGraphSuspendOperations(
            Database.connect(
                ProbingDataSource(dataSource, probe),
                databaseConfig = DatabaseConfig {
                    this.defaultFetchSize = defaultFetchSize
                }
            ),
            graphName,
        )

    // ───────────────────────── 간선(Edge) CRUD ─────────────────────────

    @Test
    @Order(33)
    fun `두 정점 사이에 간선을 생성한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2023-01-01"))
        edge.id.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
        edge.properties["since"] shouldBeEqualTo "2023-01-01"
    }

    @Test
    @Order(34)
    fun `label로 간선 목록을 조회한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, carol.id, "KNOWS")
        val edges = ops.findEdgesByLabel("KNOWS").toList()
        edges.shouldNotBeEmpty()
        edges.size shouldBeGreaterThan 1
    }

    @Test
    @Order(35)
    fun `간선을 삭제한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS")
        val deleted = ops.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()
        val edges = ops.findEdgesByLabel("KNOWS").toList()
        edges.none { it.id == edge.id }.shouldBeTrue()
    }

    // ───────────────────────── 그래프 탐색 (Traversal) ─────────────────────────

    @Test
    @Order(40)
    fun `이웃 정점을 조회한다 - OUTGOING`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        val neighbors = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 1)
        ).toList()
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Bob" }.shouldBeTrue()
    }

    @Test
    @Order(41)
    fun `이웃 정점을 조회한다 - INCOMING`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        val neighbors = ops
            .neighbors(bob.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING, maxDepth = 1))
            .toList()
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Alice" }.shouldBeTrue()
    }

    @Test
    @Order(42)
    fun `이웃 정점을 조회한다 - BOTH`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")
        val neighbors = ops
            .neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 1))
            .toList()

        neighbors.shouldNotBeEmpty()
        neighbors.size shouldBeGreaterThan 1
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃을 조회한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        val neighbors = ops
            .neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2))
            .toList()
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Carol" }.shouldBeTrue()
    }

    @Test
    @Order(50)
    fun `최단 경로를 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
    }

    @Test
    @Order(51)
    fun `연결되지 않은 경우 shortestPath는 null 반환`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val dave = ops.createVertex("Person", mapOf("name" to "Dave"))
        // 간선 없음 - alice와 dave는 연결되지 않음
        val path = ops.shortestPath(alice.id, dave.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
        path.shouldBeNull()
    }

    @Test
    @Order(52)
    fun `모든 경로를 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        // alice -> bob -> carol (직접 경로)
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        // alice -> carol (우회 경로)
        ops.createEdge(alice.id, carol.id, "KNOWS")
        val paths = ops
            .allPaths(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 5))
            .toList()
        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterThan 1
    }

    // ───────────────────────── 알고리즘 (Algorithm) ─────────────────────────

    @Test
    @Order(60)
    fun `suspend algorithms return Flow results`() = runSuspendIO {
        val a = ops.createVertex("Node", mapOf("name" to "A"))
        val b = ops.createVertex("Node", mapOf("name" to "B"))
        val c = ops.createVertex("Node", mapOf("name" to "C"))
        val d = ops.createVertex("Node", mapOf("name" to "D"))

        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")
        ops.createEdge(c.id, d.id, "E")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "E"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1

        val bfs = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3)).toList()
        bfs.first().vertex.id shouldBeEqualTo a.id
        bfs.size shouldBeGreaterThan 2

        val dfs = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3)).toList()
        dfs.first().vertex.id shouldBeEqualTo a.id
        dfs.size shouldBeGreaterThan 2

        ops.detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5)).toList().shouldNotBeEmpty()
        ops.connectedComponents(ComponentOptions(vertexLabel = "Node", edgeLabel = "E")).toList().shouldNotBeEmpty()
        ops.pageRank(PageRankOptions(vertexLabel = "Node", iterations = 20)).toList().shouldNotBeEmpty()
    }
}

private class StreamingProbe(
    val failAfterFirstRow: Boolean = false,
) {
    val fetchSizes = CopyOnWriteArrayList<Int>()
    val streamingAttempts = AtomicInteger()
}

private class ProbingDataSource(
    private val delegate: DataSource,
    private val probe: StreamingProbe,
): DataSource by delegate {

    override fun getConnection(): Connection = probingConnection(delegate.connection, probe)

    override fun getConnection(username: String?, password: String?): Connection =
        probingConnection(delegate.getConnection(username, password), probe)
}

@Suppress("UNCHECKED_CAST")
private fun probingConnection(delegate: Connection, probe: StreamingProbe): Connection =
    Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        if (method.name != "prepareStatement") {
            invokeDelegate(delegate, method, args)
        } else {
            val prepared = invokeDelegate(delegate, method, args)
            if (prepared is PreparedStatement) probingPreparedStatement(prepared, probe) else prepared
        }
    } as Connection

@Suppress("UNCHECKED_CAST")
private fun probingPreparedStatement(delegate: PreparedStatement, probe: StreamingProbe): PreparedStatement {
    var fetchSize = 0
    return Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, args ->
        when (method.name) {
            "setFetchSize" -> {
                fetchSize = args?.firstOrNull() as? Int ?: 0
                probe.fetchSizes += fetchSize
                invokeDelegate(delegate, method, args)
            }

            "executeQuery" -> {
                val result = invokeDelegate(delegate, method, args)
                if (fetchSize <= 0) {
                    result
                } else {
                    probe.streamingAttempts.incrementAndGet()
                    if (probe.failAfterFirstRow) {
                        probingResultSet(result as ResultSet)
                    } else {
                        result
                    }
                }
            }

            else -> invokeDelegate(delegate, method, args)
        }
    } as PreparedStatement
}

@Suppress("UNCHECKED_CAST")
private fun probingResultSet(delegate: ResultSet): ResultSet {
    var emittedRows = 0
    return Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, args ->
        if (method.name != "next") {
            invokeDelegate(delegate, method, args)
        } else if (emittedRows > 0) {
            throw SQLException("injected late AGE streaming failure")
        } else {
            val hasNext = invokeDelegate(delegate, method, args) as Boolean
            if (hasNext) emittedRows++
            hasNext
        }
    } as ResultSet
}

private fun invokeDelegate(target: Any, method: Method, args: Array<out Any?>?): Any? =
    try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

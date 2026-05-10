package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgeGraphBatchOperationsTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ops: AgeGraphOperations
    private lateinit var suspendOps: AgeGraphSuspendOperations

    private val graphName = "test_batch_graph"

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
        Database.connect(dataSource)
        ops = AgeGraphOperations(graphName)
        suspendOps = AgeGraphSuspendOperations(graphName)
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

    @Test
    fun `createVertices returns vertices in input order`() = runSuspendIO {
        val vertices = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice", "rank" to 1L),
                mapOf("name" to "Bob", "rank" to 2L),
                mapOf("name" to "Carol", "rank" to 3L),
            ),
        )

        vertices.shouldHaveSize(3)
        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob", "Carol")
        vertices.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)
        ops.countVertices("Person") shouldBeEqualTo 3L
    }

    @Test
    fun `empty and size one batches preserve write semantics`() = runSuspendIO {
        ops.createVertices("Person", emptyList()).shouldBeEmpty()
        ops.createEdges("KNOWS", emptyList()).shouldBeEmpty()

        val vertices = ops.createVertices("Person", listOf(mapOf("name" to "Solo")))
        val edge = ops.createEdges("SELF", listOf(BatchEdge(vertices.single().id, vertices.single().id))).single()

        vertices.single().properties["name"] shouldBeEqualTo "Solo"
        edge.startId shouldBeEqualTo vertices.single().id
        edge.endId shouldBeEqualTo vertices.single().id
        ops.countVertices("Person") shouldBeEqualTo 1L
        ops.findEdgesByLabel("SELF").shouldHaveSize(1)
    }

    @Test
    fun `createVertices supports mixed property key groups while preserving order`() = runSuspendIO {
        val vertices = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob", "city" to "Seoul"),
                mapOf("name" to "Carol"),
            ),
        )

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Alice", "Bob", "Carol")
        vertices.map { it.id }.toSet().shouldHaveSize(4)
        vertices[2].properties["city"] shouldBeEqualTo "Seoul"
    }

    @Test
    fun `createEdges returns edges in input order`() = runSuspendIO {
        val (alice, bob, carol) = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
                mapOf("name" to "Carol"),
            ),
        )

        val edges = ops.createEdges(
            "KNOWS",
            listOf(
                BatchEdge(alice.id, bob.id, mapOf("rank" to 1L)),
                BatchEdge(bob.id, carol.id, mapOf("rank" to 2L)),
                BatchEdge(carol.id, alice.id, mapOf("rank" to 3L)),
            ),
        )

        edges.shouldHaveSize(3)
        edges.map { it.startId } shouldBeEqualTo listOf(alice.id, bob.id, carol.id)
        edges.map { it.endId } shouldBeEqualTo listOf(bob.id, carol.id, alice.id)
        edges.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(3)
    }

    @Test
    fun `createEdges rejects missing endpoint without partial writes`() = runSuspendIO {
        val (alice, bob) = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
            ),
        )

        assertFailsWith<GraphQueryException> {
            ops.createEdges(
                "KNOWS",
                listOf(
                    BatchEdge(alice.id, bob.id),
                    BatchEdge(alice.id, GraphElementId.of(999999999L)),
                ),
            )
        }

        ops.findEdgesByLabel("KNOWS").shouldBeEmpty()
    }

    @Test
    fun `suspend createVertices and createEdges preserve input order`() = runSuspendIO {
        val vertices = suspendOps.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
            ),
        )

        val edges = suspendOps.createEdges(
            "KNOWS",
            listOf(
                BatchEdge(vertices[0].id, vertices[1].id, mapOf("rank" to 1L)),
                BatchEdge(vertices[1].id, vertices[0].id, mapOf("rank" to 2L)),
            ),
        )

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")
        edges.map { it.startId } shouldBeEqualTo listOf(vertices[0].id, vertices[1].id)
        edges.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L)
        suspendOps.findEdgesByLabel("KNOWS").toList().shouldHaveSize(2)
    }
}

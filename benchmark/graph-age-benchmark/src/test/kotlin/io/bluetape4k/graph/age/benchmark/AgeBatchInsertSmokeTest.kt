package io.bluetape4k.graph.age.benchmark

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

class AgeBatchInsertSmokeTest {

    @Test
    fun `10k loop and batch insert smoke`() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 4
        }).use { dataSource ->
            Database.connect(dataSource)
            val graphName = "batch_smoke_graph"
            val result = runSmoke("age") {
                AgeGraphOperations(graphName).also { ops ->
                    if (ops.graphExists(graphName)) {
                        ops.dropGraph(graphName)
                    }
                    ops.createGraph(graphName)
                }
            }

            result.rows shouldBeEqualTo 10_000
            println(result.toSummaryLine())
        }
    }

    private fun runSmoke(
        backend: String,
        rows: Int = 10_000,
        createOperations: () -> GraphOperations,
    ): SmokeResult {
        val vertexRows = (0 until rows).map { index ->
            mapOf("name" to "Person-$index", "rank" to index.toLong())
        }

        val vertexLoopMs = createOperations().let { ops ->
            measureMs {
                vertexRows.forEach { props -> ops.createVertex("Person", props) }
            }
        }
        val vertexBatchMs = createOperations().let { ops ->
            measureMs { ops.createVertices("Person", vertexRows) }
        }
        val edgeLoopMs = createOperations().let { ops ->
            val vertices = ops.createVertices("Person", vertexRows)
            val edges = cycleEdges(vertices)
            measureMs {
                edges.forEach { edge -> ops.createEdge(edge.fromId, edge.toId, "KNOWS", edge.properties) }
            }
        }
        val edgeBatchMs = createOperations().let { ops ->
            val vertices = ops.createVertices("Person", vertexRows)
            val edges = cycleEdges(vertices)
            measureMs { ops.createEdges("KNOWS", edges) }
        }

        return SmokeResult(backend, rows, vertexLoopMs, vertexBatchMs, edgeLoopMs, edgeBatchMs)
    }

    private fun cycleEdges(vertices: List<GraphVertex>): List<BatchEdge> =
        vertices.indices.map { index ->
            BatchEdge(
                fromId = vertices[index].id,
                toId = vertices[(index + 1) % vertices.size].id,
                properties = mapOf("rank" to index.toLong()),
            )
        }

    private fun measureMs(block: () -> Unit): Long =
        (measureNanoTime(block) / 1_000_000.0).roundToLong()

    private data class SmokeResult(
        val backend: String,
        val rows: Int,
        val vertexLoopMs: Long,
        val vertexBatchMs: Long,
        val edgeLoopMs: Long,
        val edgeBatchMs: Long,
    ) {
        fun toSummaryLine(): String =
            "batch-smoke backend=$backend rows=$rows vertexLoopMs=$vertexLoopMs vertexBatchMs=$vertexBatchMs " +
                "vertexSpeedup=${formatSpeedup(vertexLoopMs, vertexBatchMs)} edgeLoopMs=$edgeLoopMs " +
                "edgeBatchMs=$edgeBatchMs edgeSpeedup=${formatSpeedup(edgeLoopMs, edgeBatchMs)}"

        private fun formatSpeedup(loopMs: Long, batchMs: Long): String =
            "%.1f".format(loopMs.toDouble() / batchMs.coerceAtLeast(1))
    }
}

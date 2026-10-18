package io.bluetape4k.graph.neo4j.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.Test
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import kotlin.system.measureTimeMillis

class Neo4jBatchInsertSmokeTest {

    companion object: KLogging() {
        private val server = Neo4jServer.Launcher.neo4j
    }

    @Test
    fun `10k loop and batch insert smoke`() {

        GraphDatabase.driver(server.boltUrl, AuthTokens.none()).use { driver ->
            val result = runSmoke("neo4j") {
                Neo4jGraphOperations(driver).also { it.dropGraph("neo4j") }
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
        val vertexRows = List(rows) { index ->
            mapOf("name" to "Person-$index", "rank" to index.toLong())
        }

        val vertexLoopMs = createOperations().let { ops ->
            measureTimeMillis {
                vertexRows.forEach { props ->
                    ops.createVertex("Person", props)
                }
            }
        }
        val vertexBatchMs = createOperations().let { ops ->
            measureTimeMillis {
                ops.createVertices("Person", vertexRows)
            }
        }
        val edgeLoopMs = createOperations().let { ops ->
            val vertices = ops.createVertices("Person", vertexRows)
            val edges = cycleEdges(vertices)
            measureTimeMillis {
                edges.forEach { edge ->
                    ops.createEdge(edge.fromId, edge.toId, "KNOWS", edge.properties)
                }
            }
        }
        val edgeBatchMs = createOperations().let { ops ->
            val vertices = ops.createVertices("Person", vertexRows)
            val edges = cycleEdges(vertices)
            measureTimeMillis {
                ops.createEdges("KNOWS", edges)
            }
        }

        return SmokeResult(backend, rows, vertexLoopMs, vertexBatchMs, edgeLoopMs, edgeBatchMs)
    }

    private fun cycleEdges(vertices: List<GraphVertex>): List<BatchEdge> =
        vertices.indices.map { index ->
            BatchEdge(
                fromId = vertices[index].id,
                toId = vertices[(index + 1) % vertices.size].id,
                "rank" to index.toLong(),
            )
        }

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

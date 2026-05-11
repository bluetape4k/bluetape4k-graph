package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

data class BatchInsertSmokeResult(
    val backend: String,
    val rows: Int,
    val vertexLoopMs: Long,
    val vertexBatchMs: Long,
    val edgeLoopMs: Long,
    val edgeBatchMs: Long,
) {
    val vertexSpeedup: Double = vertexLoopMs.toDouble() / vertexBatchMs.coerceAtLeast(1)
    val edgeSpeedup: Double = edgeLoopMs.toDouble() / edgeBatchMs.coerceAtLeast(1)

    fun toSummaryLine(): String =
        "batch-smoke backend=$backend rows=$rows vertexLoopMs=$vertexLoopMs vertexBatchMs=$vertexBatchMs " +
            "vertexSpeedup=${vertexSpeedup.format1()} edgeLoopMs=$edgeLoopMs edgeBatchMs=$edgeBatchMs " +
            "edgeSpeedup=${edgeSpeedup.format1()}"
}

fun runBatchInsertSmoke(
    backend: String,
    rows: Int = 10_000,
    createOperations: () -> GraphOperations,
): BatchInsertSmokeResult {
    val vertexRows = (0 until rows).map { index ->
        mapOf("name" to "Person-$index", "rank" to index.toLong())
    }

    val vertexLoopMs = createOperations().use { ops ->
        measureMs {
            vertexRows.forEach { props ->
                ops.createVertex("Person", props)
            }
        }
    }

    val vertexBatchMs = createOperations().use { ops ->
        measureMs {
            ops.createVertices("Person", vertexRows)
        }
    }

    val edgeLoopMs = createOperations().use { ops ->
        val vertices = ops.createVertices("Person", vertexRows)
        val edges = cycleEdges(vertices)
        measureMs {
            edges.forEach { edge ->
                ops.createEdge(edge.fromId, edge.toId, "KNOWS", edge.properties)
            }
        }
    }

    val edgeBatchMs = createOperations().use { ops ->
        val vertices = ops.createVertices("Person", vertexRows)
        val edges = cycleEdges(vertices)
        measureMs {
            ops.createEdges("KNOWS", edges)
        }
    }

    return BatchInsertSmokeResult(
        backend = backend,
        rows = rows,
        vertexLoopMs = vertexLoopMs,
        vertexBatchMs = vertexBatchMs,
        edgeLoopMs = edgeLoopMs,
        edgeBatchMs = edgeBatchMs,
    )
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

private fun Double.format1(): String =
    "%.1f".format(this)

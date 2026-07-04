package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.Executors

class CsvSuspendRoundTripTest {

    companion object : KLoggingChannel()

    @Test
    fun `suspend round trip two vertices and one edge`(@TempDir dir: Path) = runSuspendIO {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val sourceOps = TinkerGraphSuspendOperations()
        val alice = sourceOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = sourceOps.createVertex("Person", mapOf("name" to "Bob"))
        sourceOps.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))

        val exporter = SuspendCsvGraphBulkExporter()
        exporter.exportGraphSuspending(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            sourceOps,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val targetOps = TinkerGraphSuspendOperations()
        val importer = SuspendCsvGraphBulkImporter()
        val report = importer.importGraphSuspending(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            targetOps,
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `suspend csv graph operations stay on caller dispatcher`(@TempDir dir: Path) {
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "csv-graph-caller")
        }.asCoroutineDispatcher()

        try {
            runBlocking(dispatcher) {
                val vOut = dir.resolve("caller-v.csv")
                val eOut = dir.resolve("caller-e.csv")

                val sourceOps = ThreadRecordingSuspendOperations(TinkerGraphSuspendOperations())
                val alice = sourceOps.createVertex("Person", mapOf("name" to "Alice"))
                val bob = sourceOps.createVertex("Person", mapOf("name" to "Bob"))
                sourceOps.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))
                sourceOps.clear()

                SuspendCsvGraphBulkExporter().exportGraphSuspending(
                    CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
                    sourceOps,
                    GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
                )

                sourceOps.recordedThreads.isNotEmpty().shouldBeTrue()
                sourceOps.recordedThreads.all { it.startsWith("csv-graph-caller") }.shouldBeTrue()

                val targetOps = ThreadRecordingSuspendOperations(TinkerGraphSuspendOperations())
                SuspendCsvGraphBulkImporter().importGraphSuspending(
                    CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
                    targetOps,
                    GraphImportOptions(),
                )

                targetOps.recordedThreads.isNotEmpty().shouldBeTrue()
                targetOps.recordedThreads.all { it.startsWith("csv-graph-caller") }.shouldBeTrue()
            }
        } finally {
            dispatcher.close()
        }
    }

    private class ThreadRecordingSuspendOperations(
        private val delegate: GraphSuspendOperations,
    ): GraphSuspendOperations by delegate {

        val recordedThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

        fun clear() {
            recordedThreads.clear()
        }

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
            record()
            return delegate.findVerticesByLabel(label, filter)
        }

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
            record()
            return delegate.findEdgesByLabel(label, filter)
        }

        override suspend fun createVertices(
            label: String,
            propertiesList: List<Map<String, Any?>>,
        ): List<GraphVertex> {
            record()
            return delegate.createVertices(label, propertiesList)
        }

        override suspend fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
            record()
            return delegate.createEdges(label, edges)
        }

        private fun record() {
            recordedThreads.add(Thread.currentThread().name)
        }
    }
}

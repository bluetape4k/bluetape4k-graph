package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.Executors

class GraphMlSuspendTest {

    @Test
    fun `suspend import and export round trip`(@TempDir dir: Path) = runSuspendIO {
        val out = dir.resolve("graph-suspend.graphml")

        val src = TinkerGraphOperations()
        val a = src.createVertex("Product", mapOf("name" to "Widget", "price" to 9.99))
        val b = src.createVertex("Product", mapOf("name" to "Gadget", "price" to 19.99))
        src.createEdge(a.id, b.id, "SIMILAR", mapOf("score" to 0.8))

        val suspendSrc = TinkerGraphSuspendOperations(src)
        val suspendTarget = TinkerGraphSuspendOperations(TinkerGraphOperations())

        val exportReport = SuspendGraphMlBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(out),
            suspendSrc,
            GraphExportOptions(vertexLabels = setOf("Product"), edgeLabels = setOf("SIMILAR")),
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(out),
            suspendTarget,
            GraphImportOptions(),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `suspend graphml graph operations stay on caller dispatcher`(@TempDir dir: Path) {
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "graphml-graph-caller")
        }.asCoroutineDispatcher()

        try {
            runBlocking(dispatcher) {
                val out = dir.resolve("caller.graphml")

                val sourceOps = ThreadRecordingSuspendOperations(TinkerGraphSuspendOperations())
                val alice = sourceOps.createVertex("Product", mapOf("name" to "Widget"))
                val bob = sourceOps.createVertex("Product", mapOf("name" to "Gadget"))
                sourceOps.createEdge(alice.id, bob.id, "SIMILAR", mapOf("score" to 0.8))
                sourceOps.clear()

                SuspendGraphMlBulkExporter().exportGraphSuspending(
                    GraphExportSink.PathSink(out),
                    sourceOps,
                    GraphExportOptions(vertexLabels = setOf("Product"), edgeLabels = setOf("SIMILAR")),
                )

                sourceOps.recordedThreads.isNotEmpty().shouldBeTrue()
                sourceOps.recordedThreads.all { it.startsWith("graphml-graph-caller") }.shouldBeTrue()

                val targetOps = ThreadRecordingSuspendOperations(TinkerGraphSuspendOperations(TinkerGraphOperations()))
                SuspendGraphMlBulkImporter().importGraphSuspending(
                    GraphImportSource.PathSource(out),
                    targetOps,
                    GraphImportOptions(),
                )

                targetOps.recordedThreads.isNotEmpty().shouldBeTrue()
                targetOps.recordedThreads.all { it.startsWith("graphml-graph-caller") }.shouldBeTrue()
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

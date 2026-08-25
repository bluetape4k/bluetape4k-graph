package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.Executors
import javax.xml.stream.XMLStreamException

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
    fun `suspend export uses chunked repository API without full label materialization`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("chunked-suspend.graphml")
        val src = TinkerGraphSuspendOperations()
        val vertices = (1..5).map { index ->
            src.createVertex("Person", mapOf("name" to "Person-$index"))
        }
        src.createEdge(vertices[0].id, vertices[1].id, "KNOWS", mapOf("rank" to 1))
        src.createEdge(vertices[1].id, vertices[2].id, "KNOWS", mapOf("rank" to 2))
        val requestedChunkSizes = mutableListOf<Int>()

        val report = SuspendGraphMlBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(out),
            ChunkOnlyGraphSuspendOperations(src, requestedChunkSizes),
            GraphExportOptions(
                vertexLabels = setOf("Person"),
                edgeLabels = setOf("KNOWS"),
                exportChunkSize = 2,
            ),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 5L
        report.edgesWritten shouldBeEqualTo 2L
        requestedChunkSizes shouldBeEqualTo listOf(2, 2)
    }

    @Test
    fun `suspend export freezes the first chunk before backend mutation`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("mutation-suspend.graphml")
        val vertexProperties = linkedMapOf<String, Any?>("name" to "before")
        val edgeProperties = linkedMapOf<String, Any?>("state" to "before")
        val backend = MutatingChunkOnlyGraphSuspendOperations(
            vertex = GraphVertex(GraphElementId.of("v-1"), "Person", vertexProperties),
            edge = GraphEdge(
                GraphElementId.of("e-1"),
                "KNOWS",
                GraphElementId.of("v-1"),
                GraphElementId.of("v-2"),
                edgeProperties,
            ),
            vertexProperties = vertexProperties,
            edgeProperties = edgeProperties,
        )

        val report = SuspendGraphMlBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(out),
            backend,
            GraphExportOptions(
                vertexLabels = setOf("Person"),
                edgeLabels = setOf("KNOWS"),
                exportChunkSize = 1,
            ),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 1L
        report.edgesWritten shouldBeEqualTo 1L
        backend.requests shouldBeEqualTo listOf("vertices:1", "edges:1")
        java.nio.file.Files.readString(out).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
    }

    @Test
    fun `suspend export preserves the primary sink failure while closing spool`() = runSuspendIO {
        val source = TinkerGraphSuspendOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        val thrown = assertFailsWith<XMLStreamException> {
            SuspendGraphMlBulkExporter().exportGraphSuspending(
                GraphExportSink.OutputStreamSink(FailingOutputStream("graphml-suspend-sink-failure")),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }

        generateSequence<Throwable>(thrown) { it.cause }
            .any { it.message?.contains("graphml-suspend-sink-failure") == true }
            .shouldBeTrue()
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

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> {
            record()
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> {
            record()
            return delegate.findEdgesByLabelChunked(label, filter, chunkSize)
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

    private class ChunkOnlyGraphSuspendOperations(
        private val delegate: GraphSuspendOperations,
        private val requestedChunkSizes: MutableList<Int>,
    ) : GraphSuspendOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> =
            error("full vertex Flow lookup must not be used by GraphML export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            error("full edge Flow lookup must not be used by GraphML export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> {
            requestedChunkSizes += chunkSize
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> {
            requestedChunkSizes += chunkSize
            return delegate.findEdgesByLabelChunked(label, filter, chunkSize)
        }
    }

    private class MutatingChunkOnlyGraphSuspendOperations(
        private val vertex: GraphVertex,
        private val edge: GraphEdge,
        private val vertexProperties: MutableMap<String, Any?>,
        private val edgeProperties: MutableMap<String, Any?>,
    ) : GraphSuspendOperations by TinkerGraphSuspendOperations() {

        val requests = Collections.synchronizedList(mutableListOf<String>())

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> =
            error("full vertex Flow lookup must not be used by GraphML export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            error("full edge Flow lookup must not be used by GraphML export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> = kotlinx.coroutines.flow.flow {
            requests += "vertices:$chunkSize"
            emit(listOf(vertex))
            vertexProperties["name"] = "after"
            emit(emptyList())
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> = kotlinx.coroutines.flow.flow {
            requests += "edges:$chunkSize"
            emit(listOf(edge))
            edgeProperties["state"] = "after"
            emit(emptyList())
        }
    }

    private class FailingOutputStream(
        private val message: String,
    ) : OutputStream() {
        override fun write(b: Int): Unit = throw IOException(message)

        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException(message)
    }
}

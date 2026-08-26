package io.bluetape4k.graph.io.csv

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
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

    @Test
    fun `suspend export uses bounded chunks once per label and unions properties`(@TempDir dir: Path) = runSuspendIO {
        val vOut = dir.resolve("bounded-suspend-v.csv")
        val eOut = dir.resolve("bounded-suspend-e.csv")
        val source = TinkerGraphSuspendOperations()
        val person = source.createVertex("Person", mapOf("name" to "Alice"))
        val company = source.createVertex("Company", mapOf("industry" to "Software"))
        source.createEdge(person.id, company.id, "WORKS", mapOf("since" to 2024))
        val requests = Collections.synchronizedList(mutableListOf<String>())

        val report = SuspendCsvGraphBulkExporter().exportGraphSuspending(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            ChunkOnlyGraphSuspendOperations(source, requests),
            GraphExportOptions(
                vertexLabels = setOf("Person", "Company"),
                edgeLabels = setOf("WORKS"),
                exportChunkSize = 1,
            ),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
        report.edgesWritten shouldBeEqualTo 1L
        requests.sorted() shouldBeEqualTo listOf("Company:1", "Person:1", "WORKS:1")
        java.nio.file.Files.readString(vOut) shouldContain "prop.industry"
        java.nio.file.Files.readString(vOut) shouldContain "prop.name"
    }

    @Test
    fun `suspend export freezes the first chunk before backend mutation`(@TempDir dir: Path) = runSuspendIO {
        val vOut = dir.resolve("mutation-suspend-v.csv")
        val eOut = dir.resolve("mutation-suspend-e.csv")
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

        val report = SuspendCsvGraphBulkExporter().exportGraphSuspending(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
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
        java.nio.file.Files.readString(vOut).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
        java.nio.file.Files.readString(eOut).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
    }

    @Test
    fun `suspend export cancels source collection and completes spool cleanup`() = runTest {
        val cancelled = AtomicBoolean(false)
        val exporter = SuspendCsvGraphBulkExporter()
        val job = launch {
            exporter.exportGraphSuspending(
                CsvGraphExportSink(
                    GraphExportSink.OutputStreamSink(java.io.ByteArrayOutputStream(), closeOutput = false),
                    GraphExportSink.OutputStreamSink(java.io.ByteArrayOutputStream(), closeOutput = false),
                ),
                CancellingGraphSuspendOperations(cancelled),
                GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            )
        }

        runCurrent()
        job.cancelAndJoin()
        cancelled.get().shouldBeTrue()
    }

    @Test
    fun `suspend export preserves the primary sink failure while closing spool`() = runSuspendIO {
        val source = TinkerGraphSuspendOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        val thrown = assertFailsWith<IOException> {
            SuspendCsvGraphBulkExporter().exportGraphSuspending(
                CsvGraphExportSink(
                    GraphExportSink.OutputStreamSink(FailingOutputStream("csv-suspend-sink-failure")),
                    GraphExportSink.OutputStreamSink(java.io.ByteArrayOutputStream()),
                ),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }

        thrown.message shouldBeEqualTo "csv-suspend-sink-failure"
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
        private val requests: MutableList<String>,
    ) : GraphSuspendOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> =
            error("full vertex Flow lookup must not be used by CSV export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            error("full edge Flow lookup must not be used by CSV export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> {
            requests += "$label:$chunkSize"
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> {
            requests += "$label:$chunkSize"
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
            error("full vertex Flow lookup must not be used by CSV export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            error("full edge Flow lookup must not be used by CSV export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> = flow {
            requests += "vertices:$chunkSize"
            emit(listOf(vertex))
            vertexProperties["name"] = "after"
            emit(emptyList())
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> = flow {
            requests += "edges:$chunkSize"
            emit(listOf(edge))
            edgeProperties["state"] = "after"
            emit(emptyList())
        }
    }

    private class CancellingGraphSuspendOperations(
        private val cancelled: AtomicBoolean,
    ) : GraphSuspendOperations by TinkerGraphSuspendOperations() {

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> = flow {
            try {
                emit(listOf(GraphVertex(io.bluetape4k.graph.model.GraphElementId.of("v-1"), label)))
                awaitCancellation()
            } finally {
                cancelled.set(true)
            }
        }
    }

    private class FailingOutputStream(
        private val message: String,
    ) : OutputStream() {
        override fun write(b: Int): Unit = throw IOException(message)

        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException(message)
    }
}

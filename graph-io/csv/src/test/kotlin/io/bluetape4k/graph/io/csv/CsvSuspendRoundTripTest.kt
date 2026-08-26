package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
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

    @Test
    fun `suspend csv replay checkpoints cancellation between records`() = runSuspendIO {
        val cancellation = CancellationException("csv-replay-cancelled")
        val deferredRef = AtomicReference<kotlinx.coroutines.Deferred<GraphExportReport>?>(null)
        val vertices = CancellingAfterMarkerOutputStream("row-0") {
            deferredRef.get()?.cancel(cancellation)
        }
        val source = TinkerGraphSuspendOperations().also {
            repeat(4) { index ->
                it.createVertex("Person", mapOf("name" to "row-$index-${"x".repeat(9_000)}"))
            }
        }
        val deferred = async(start = CoroutineStart.LAZY) {
            SuspendCsvGraphBulkExporter().exportGraphSuspending(
                CsvGraphExportSink(
                    GraphExportSink.OutputStreamSink(vertices, closeOutput = false),
                    GraphExportSink.OutputStreamSink(ByteArrayOutputStream(), closeOutput = false),
                ),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }
        deferredRef.set(deferred)
        deferred.start()

        val thrown = assertFailsWith<CancellationException> { deferred.await() }
        thrown.message shouldBeEqualTo cancellation.message
        vertices.text shouldContain "row-0"
        vertices.text shouldNotContain "row-3"
    }

    @Test
    fun `suspend csv keeps caller owned output streams open`() = runSuspendIO {
        val vertices = TrackingOutputStream()
        val edges = TrackingOutputStream()
        val source = TinkerGraphSuspendOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        SuspendCsvGraphBulkExporter().exportGraphSuspending(
            CsvGraphExportSink(
                GraphExportSink.OutputStreamSink(vertices, closeOutput = false),
                GraphExportSink.OutputStreamSink(edges, closeOutput = false),
            ),
            source,
            GraphExportOptions(vertexLabels = setOf("Person")),
        )

        vertices.closed shouldBeEqualTo false
        edges.closed shouldBeEqualTo false
        vertices.write('!'.code)
        edges.write('!'.code)
    }

    @Test
    fun `suspend csv closes owned output streams`() = runSuspendIO {
        val vertices = TrackingOutputStream()
        val edges = TrackingOutputStream()
        val source = TinkerGraphSuspendOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        SuspendCsvGraphBulkExporter().exportGraphSuspending(
            CsvGraphExportSink(
                GraphExportSink.OutputStreamSink(vertices, closeOutput = true),
                GraphExportSink.OutputStreamSink(edges, closeOutput = true),
            ),
            source,
            GraphExportOptions(vertexLabels = setOf("Person")),
        )

        vertices.closed shouldBeEqualTo true
        edges.closed shouldBeEqualTo true
    }

    @Test
    fun `suspend csv preserves cancellation when owned sink close fails`() = runSuspendIO {
        val cancellation = CancellationException("csv-replay-cancelled-close-failure")
        val vertices = CancellingAfterMarkerOutputStream(
            marker = "row-0",
            closeFailure = "csv-close-failure",
            failure = cancellation,
        )
        val source = TinkerGraphSuspendOperations().also {
            repeat(4) { index ->
                it.createVertex("Person", mapOf("name" to "row-$index-${"x".repeat(9_000)}"))
            }
        }
        val thrown = assertFailsWith<CancellationException> {
            SuspendCsvGraphBulkExporter().exportGraphSuspending(
                CsvGraphExportSink(
                    GraphExportSink.OutputStreamSink(vertices, closeOutput = true),
                    GraphExportSink.OutputStreamSink(ByteArrayOutputStream(), closeOutput = true),
                ),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }

        thrown.message shouldBeEqualTo cancellation.message
        thrown.suppressed.any { it.message == "csv-close-failure" }.shouldBeTrue()
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

    private class CancellingAfterMarkerOutputStream(
        private val marker: String,
        private val closeFailure: String? = null,
        private val failure: Throwable? = null,
        private val onMarker: () -> Unit = {},
    ) : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        private var triggered = false

        val text: String
            get() = delegate.toString(Charsets.UTF_8.name())

        override fun write(b: Int) {
            delegate.write(b)
            triggerIfMatched()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            triggerIfMatched()
        }

        override fun close() {
            closeFailure?.let { throw IOException(it) }
        }

        private fun triggerIfMatched() {
            if (!triggered && text.contains(marker)) {
                triggered = true
                onMarker()
                failure?.let { throw it }
            }
        }
    }

    private class TrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var closed: Boolean = false
            private set

        override fun write(b: Int) {
            check(!closed) { "stream is closed" }
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "stream is closed" }
            delegate.write(b, off, len)
        }

        override fun close() {
            closed = true
        }
    }
}

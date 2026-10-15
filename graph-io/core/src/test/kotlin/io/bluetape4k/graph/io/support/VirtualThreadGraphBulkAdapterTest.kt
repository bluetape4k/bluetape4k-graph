package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.concurrent.await
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class VirtualThreadGraphBulkAdapterTest {

    companion object: KLogging()

    private val stubImportReport = GraphImportReport(
        status = GraphIoStatus.COMPLETED,
        format = GraphIoFormat.CSV,
        verticesRead = 0L,
        verticesCreated = 0L,
        edgesRead = 0L,
        edgesCreated = 0L,
        skippedVertices = 0L,
        skippedEdges = 0L,
        elapsed = Duration.ZERO
    )
    private val stubExportReport = GraphExportReport(
        status = GraphIoStatus.COMPLETED,
        format = GraphIoFormat.CSV,
        verticesWritten = 0L,
        edgesWritten = 0L,
        elapsed = Duration.ZERO,
    )

    private fun stubImporter(block: (String, GraphOperations, GraphImportOptions) -> GraphImportReport) =
        object: GraphBulkImporter<String> {
            override fun importGraph(source: String, operations: GraphOperations, options: GraphImportOptions) =
                block(source, operations, options)
        }

    private fun stubExporter(block: (String, GraphOperations, GraphExportOptions) -> GraphExportReport) =
        object: GraphBulkExporter<String> {
            override fun exportGraph(sink: String, operations: GraphOperations, options: GraphExportOptions) =
                block(sink, operations, options)
        }

    // ── wrapImporter ─────────────────────────────────────────────────────────

    @Test
    fun `importAsync wraps sync importer with virtual thread future`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = importer.asVirtualThreadImporter()
        vt.importGraphAsync(
            source = "src",
            operations = FakeGraphOperations(),
            options = GraphImportOptions()
        ).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync with default options uses interface default parameter`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = importer.asVirtualThreadImporter()

        // omit options → triggers GraphVirtualThreadBulkImporter DefaultImpls stub
        vt.importGraphAsync(
            source = "src",
            operations = FakeGraphOperations()
        ).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync propagates sync failure`() {
        val boom = RuntimeException("boom")
        val importer = stubImporter { _, _, _ -> throw boom }
        val vt = importer.asVirtualThreadImporter()

        val ee = assertFailsWith<ExecutionException> {
            vt.importGraphAsync(
                source = "x",
                operations = FakeGraphOperations(),
                options = GraphImportOptions()
            ).get()
        }
        log.debug { "ee=$ee" }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }

    @Test
    fun `import adapter closes sync importer exactly once`() {
        val closeCalls = AtomicInteger()
        val importer = object: GraphBulkImporter<String> {
            override fun importGraph(source: String, operations: GraphOperations, options: GraphImportOptions) =
                stubImportReport

            override fun close() {
                closeCalls.incrementAndGet()
            }
        }
        val vt = importer.asVirtualThreadImporter()

        vt.close()
        vt.close()

        closeCalls.get() shouldBeEqualTo 1
    }

    // ── wrapExporter ─────────────────────────────────────────────────────────

    @Test
    fun `exportAsync wraps sync exporter with virtual thread future`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = exporter.asVirtualThreadExporter()

        vt.exportGraphAsync(
            sink = "sink",
            operations = FakeGraphOperations(),
            options = GraphExportOptions()
        ).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync with default options uses interface default parameter`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = exporter.asVirtualThreadExporter()

        // omit options → triggers GraphVirtualThreadBulkExporter DefaultImpls stub
        vt.exportGraphAsync(
            sink = "sink",
            operations = FakeGraphOperations()
        ).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync propagates sync failure`() {
        val boom = RuntimeException("export boom")
        val exporter = stubExporter { _, _, _ -> throw boom }
        val vt = exporter.asVirtualThreadExporter()

        val ee = assertFailsWith<ExecutionException> {
            vt.exportGraphAsync(
                "sink",
                FakeGraphOperations(),
                GraphExportOptions()
            ).get()
        }
        log.debug { "ee=$ee" }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }

    @Test
    fun `export adapter closes sync exporter exactly once`() {
        val closeCalls = AtomicInteger()
        val exporter = object: GraphBulkExporter<String> {
            override fun exportGraph(sink: String, operations: GraphOperations, options: GraphExportOptions) =
                stubExportReport

            override fun close() {
                closeCalls.incrementAndGet()
            }
        }
        val vt = exporter.asVirtualThreadExporter()

        vt.close()
        vt.close()

        closeCalls.get() shouldBeEqualTo 1
    }

    @Test
    fun `cancel false records cancellation without interrupting running worker`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val cancelCalls = AtomicInteger()
        val cancelInterrupt = AtomicBoolean(true)

        val future = cancellableVirtualFuture(
            onCancel = { mayInterruptIfRunning, started ->
                cancelCalls.incrementAndGet()
                cancelInterrupt.set(mayInterruptIfRunning)
                started.shouldBeTrue()
            },
        ) {
            entered.countDown()
            try {
                release.await()
                42
            } catch (error: InterruptedException) {
                interrupted.set(true)
                throw error
            } finally {
                completed.countDown()
            }
        }

        entered.await(5.seconds).shouldBeTrue()
        future.cancel(false).shouldBeTrue()
        future.cancel(false).shouldBeFalse()
        release.countDown()
        completed.await(5.seconds).shouldBeTrue()

        future.isCancelled.shouldBeTrue()
        interrupted.get().shouldBeFalse()
        cancelCalls.get() shouldBeEqualTo 1
        cancelInterrupt.get().shouldBeFalse()
    }

    @Test
    fun `cancel true interrupts running worker exactly once`() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val startedAtCancel = AtomicReference<Boolean>()

        val future = cancellableVirtualFuture(
            onCancel = { mayInterruptIfRunning, started ->
                mayInterruptIfRunning.shouldBeTrue()
                startedAtCancel.set(started)
                cancelCalls.incrementAndGet()
            },
        ) {
            entered.countDown()
            try {
                CountDownLatch(1).await()
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
            42
        }

        entered.await(5.seconds).shouldBeTrue()
        future.cancel(true).shouldBeTrue()
        interrupted.await(5.seconds).shouldBeTrue()
        future.cancel(true).shouldBeFalse()

        future.isCancelled.shouldBeTrue()
        startedAtCancel.get().shouldBeTrue()
        cancelCalls.get() shouldBeEqualTo 1
    }

    @Test
    fun `cancel surfaces lifecycle listener Error after accepting cancellation`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val listenerError = AssertionError("listener failure")
        val future = cancellableVirtualFuture(
            onCancel = { _, _ -> throw listenerError },
        ) {
            entered.countDown()
            release.await()
            42
        }

        entered.await(5.seconds).shouldBeTrue()
        assertFailsWith<AssertionError> { future.cancel(false) } shouldBeEqualTo listenerError
        future.isCancelled.shouldBeTrue()
        release.countDown()
    }

    @Test
    fun `reporter cancellation terminal is deferred until running worker finishes`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val events = ConcurrentLinkedQueue<GraphIoProgressEventType>()
        
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = {
                events += it.type
                if (it.type == GraphIoProgressEventType.CANCELLED) terminal.countDown()
            },
        )
        val future = cancellableVirtualFuture(
            reporter = reporter,
            block = {
                entered.countDown()
                release.await()
                stubImportReport
            },
            onCompleted = { report -> reporter.completed(report) },
        )

        entered.await(5.seconds).shouldBeTrue()
        future.cancel(false).shouldBeTrue()

        events.toList() shouldBeEqualTo listOf(GraphIoProgressEventType.STARTED)
        
        release.countDown()
        terminal.await(5.seconds).shouldBeTrue()

        events.toList() shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.CANCELLED,
        )
        reporter.isTerminal().shouldBeTrue()
    }
}

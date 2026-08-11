package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class VirtualThreadGraphBulkAdapterTest {

    private val stubImportReport = GraphImportReport(
        GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, 0L, 0L, 0L, 0L, 0L, Duration.ZERO
    )
    private val stubExportReport = GraphExportReport(
        status = GraphIoStatus.COMPLETED,
        format = GraphIoFormat.CSV,
        verticesWritten = 0L,
        edgesWritten = 0L,
        elapsed = Duration.ZERO,
    )

    private fun stubImporter(block: (String, GraphOperations, GraphImportOptions) -> GraphImportReport) =
        object : GraphBulkImporter<String> {
            override fun importGraph(source: String, operations: GraphOperations, options: GraphImportOptions) =
                block(source, operations, options)
        }

    private fun stubExporter(block: (String, GraphOperations, GraphExportOptions) -> GraphExportReport) =
        object : GraphBulkExporter<String> {
            override fun exportGraph(sink: String, operations: GraphOperations, options: GraphExportOptions) =
                block(sink, operations, options)
        }

    // ── wrapImporter ─────────────────────────────────────────────────────────

    @Test
    fun `importAsync wraps sync importer with virtual thread future`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        vt.importGraphAsync("src", FakeGraphOperations(), GraphImportOptions()).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync with default options uses interface default parameter`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        // omit options → triggers GraphVirtualThreadBulkImporter DefaultImpls stub
        vt.importGraphAsync("src", FakeGraphOperations()).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync propagates sync failure`() {
        val boom = RuntimeException("boom")
        val importer = stubImporter { _, _, _ -> throw boom }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        val ee = assertFailsWith<ExecutionException> {
            vt.importGraphAsync("x", FakeGraphOperations(), GraphImportOptions()).get()
        }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }

    // ── wrapExporter ─────────────────────────────────────────────────────────

    @Test
    fun `exportAsync wraps sync exporter with virtual thread future`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        vt.exportGraphAsync("sink", FakeGraphOperations(), GraphExportOptions()).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync with default options uses interface default parameter`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        // omit options → triggers GraphVirtualThreadBulkExporter DefaultImpls stub
        vt.exportGraphAsync("sink", FakeGraphOperations()).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync propagates sync failure`() {
        val boom = RuntimeException("export boom")
        val exporter = stubExporter { _, _, _ -> throw boom }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        val ee = assertFailsWith<ExecutionException> {
            vt.exportGraphAsync("sink", FakeGraphOperations(), GraphExportOptions()).get()
        }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }

    @Test
    fun `cancel false records cancellation without interrupting running worker`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val cancelCalls = AtomicInteger()
        val cancelInterrupt = AtomicBoolean(true)

        val future = VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            onCancel = { mayInterruptIfRunning, started ->
                cancelCalls.incrementAndGet()
                cancelInterrupt.set(mayInterruptIfRunning)
                started shouldBeEqualTo true
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

        entered.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        future.cancel(false) shouldBeEqualTo true
        future.cancel(false) shouldBeEqualTo false
        release.countDown()
        completed.await(5, TimeUnit.SECONDS) shouldBeEqualTo true

        future.isCancelled shouldBeEqualTo true
        interrupted.get() shouldBeEqualTo false
        cancelCalls.get() shouldBeEqualTo 1
        cancelInterrupt.get() shouldBeEqualTo false
    }

    @Test
    fun `cancel true interrupts running worker exactly once`() {
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val startedAtCancel = AtomicReference<Boolean>()

        val future = VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            onCancel = { mayInterruptIfRunning, started ->
                mayInterruptIfRunning shouldBeEqualTo true
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

        entered.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        future.cancel(true) shouldBeEqualTo true
        interrupted.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        future.cancel(true) shouldBeEqualTo false

        future.isCancelled shouldBeEqualTo true
        startedAtCancel.get() shouldBeEqualTo true
        cancelCalls.get() shouldBeEqualTo 1
    }

    @Test
    fun `cancel surfaces lifecycle listener Error after accepting cancellation`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val listenerError = AssertionError("listener failure")
        val future = VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            onCancel = { _, _ -> throw listenerError },
        ) {
            entered.countDown()
            release.await()
            42
        }

        entered.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        assertFailsWith<AssertionError> { future.cancel(false) } shouldBeEqualTo listenerError
        future.isCancelled shouldBeEqualTo true
        release.countDown()
    }

    @Test
    fun `reporter cancellation terminal is deferred until running worker finishes`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<GraphIoProgressEventType>())
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener {
                events += it.type
                if (it.type == GraphIoProgressEventType.CANCELLED) terminal.countDown()
            },
        )
        val future = VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = {
                entered.countDown()
                release.await()
                stubImportReport
            },
            onCompleted = { report -> reporter.completed(report) },
        )

        entered.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        future.cancel(false) shouldBeEqualTo true
        events shouldBeEqualTo listOf(GraphIoProgressEventType.STARTED)
        release.countDown()
        terminal.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.CANCELLED,
        )
        reporter.isTerminal() shouldBeEqualTo true
    }

}

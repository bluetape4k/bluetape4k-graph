package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Collections
import java.util.concurrent.Executors

class GraphIoProgressReporterTest {

    @Test
    fun `reporter emits start progress and one terminal event`() {
        val events = mutableListOf<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener { events += it },
        )

        reporter.started()
        reporter.progress(vertices = 2L, successfulVertices = 2L, elapsed = Duration.ofMillis(1))
        reporter.completed(
            status = GraphIoStatus.COMPLETED,
            vertices = 2L,
            successfulVertices = 2L,
            elapsed = Duration.ofMillis(2),
        )
        reporter.completed(status = GraphIoStatus.COMPLETED)

        events.map { it.type } shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
        events.map { it.runId }.distinct().size shouldBeEqualTo 1
    }

    @Test
    fun `reporter isolates listener exception and continues lifecycle`() {
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = GraphIoProgressListener {
                events += it.type
                if (it.type == GraphIoProgressEventType.PROGRESS) {
                    throw IllegalStateException("must not escape")
                }
            },
        )

        reporter.started()
        reporter.progress()
        reporter.completed(status = GraphIoStatus.COMPLETED)

        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
    }

    @Test
    fun `failed report emits failed terminal event`() {
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener { events += it.type },
        )

        reporter.completed(
            GraphImportReport(
                status = GraphIoStatus.FAILED,
                format = GraphIoFormat.CSV,
                verticesRead = 1,
                verticesCreated = 0,
                edgesRead = 0,
                edgesCreated = 0,
                elapsed = Duration.ofMillis(1),
            ),
        )

        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.FAILED,
        )
    }

    @Test
    fun `reporter rejects decreasing snapshot and preserves terminal state`() {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener.NOOP,
        )
        reporter.started()
        reporter.progress(vertices = 2L)

        assertFailsWith<IllegalArgumentException> { reporter.progress(vertices = 1L) }
        reporter.completed(status = GraphIoStatus.COMPLETED, vertices = 2L)
        reporter.isTerminal() shouldBeEqualTo true
    }

    @Test
    fun `pre-start cancellation emits only one event with hasStarted false`() {
        val events = mutableListOf<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener { events += it },
        )

        val first = reporter.cancelled()
        val second = reporter.cancelled()

        first?.type shouldBeEqualTo GraphIoProgressEventType.CANCELLED
        first?.hasStarted shouldBeEqualTo false
        second.shouldBeNull()
        events.size shouldBeEqualTo 1
    }

    @Test
    fun `listener Error terminalizes reporter and is not replaced by primary failure`() {
        val listenerError = AssertionError("listener failure")
        val primary = IllegalStateException("operation failure")
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener {
                events += it.type
                if (it.type == GraphIoProgressEventType.FAILED) throw listenerError
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            reporter.run(
                block = { throw primary },
                onCompleted = {},
            )
        }

        thrown shouldBeEqualTo primary
        thrown.suppressed.toList() shouldBeEqualTo listOf(listenerError)
        reporter.isTerminal() shouldBeEqualTo true
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.FAILED,
        )
    }

    @Test
    fun `non-terminal listener Error emits cleanup terminal callback`() {
        val listenerError = AssertionError("listener failure")
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = GraphIoProgressListener {
                events += it.type
                if (it.type == GraphIoProgressEventType.PROGRESS) throw listenerError
            },
        )

        assertFailsWith<AssertionError> {
            reporter.run(
                block = { reporter.progress() },
                onCompleted = {},
            )
        } shouldBeEqualTo listenerError

        reporter.isTerminal() shouldBeEqualTo true
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.FAILED,
        )
    }

    @Test
    fun `started listener Error still reaches later cleanup delegate`() {
        val listenerError = AssertionError("listener failure")
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoCompositeProgressListener.of(
                GraphIoProgressListener {
                    if (it.type == GraphIoProgressEventType.STARTED) throw listenerError
                },
                GraphIoProgressListener { events += it.type },
            ),
        )

        assertFailsWith<AssertionError> { reporter.started() } shouldBeEqualTo listenerError
        reporter.isTerminal() shouldBeEqualTo true
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.FAILED,
        )
    }

    @Test
    fun `default snapshots are resolved while lifecycle lock is held`() {
        val events = Collections.synchronizedList(mutableListOf<GraphIoProgressEvent>())
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener { events += it },
        )
        reporter.started()

        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..32).map { executor.submit { reporter.progress() } }
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        events.count { it.type == GraphIoProgressEventType.PROGRESS } shouldBeEqualTo 32
    }

    @Test
    fun `reentrant listener callback can publish same-run progress`() {
        val events = mutableListOf<GraphIoProgressEventType>()
        lateinit var reporter: GraphIoProgressReporter
        reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = GraphIoProgressListener {
                events += it.type
                if (it.type == GraphIoProgressEventType.STARTED) {
                    reporter.progress(vertices = 1L, successfulVertices = 1L, elapsed = Duration.ofMillis(1))
                }
            },
        )

        reporter.started()
        reporter.completed(
            status = GraphIoStatus.COMPLETED,
            vertices = 1L,
            successfulVertices = 1L,
            elapsed = Duration.ofMillis(2),
        )

        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
    }

    @Test
    fun `concurrent reporters isolate run ids and preserve per-run order`() {
        val events = Collections.synchronizedList(mutableListOf<GraphIoProgressEvent>())
        fun newReporter() = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = GraphIoProgressListener { events += it },
        )
        val reporters = listOf(newReporter(), newReporter())
        val executor = Executors.newFixedThreadPool(2)
        try {
            reporters.map { reporter ->
                executor.submit {
                    reporter.run(
                        block = { Thread.yield(); Unit },
                        onCompleted = { reporter.completed(status = GraphIoStatus.COMPLETED) },
                    )
                }
            }.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        events.map { it.runId }.distinct().size shouldBeEqualTo 2
        events.groupBy { it.runId }.values.forEach { runEvents ->
            runEvents.first().type shouldBeEqualTo GraphIoProgressEventType.STARTED
            runEvents.last().type shouldBeEqualTo GraphIoProgressEventType.COMPLETED
        }
    }
}

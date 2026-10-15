package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.javatimes.millis
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class GraphIoProgressReporterTest {

    companion object: KLogging()

    @Test
    fun `reporter emits start progress and one terminal event`() {
        val events = mutableListOf<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = { events += it },
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

        events.forEach { log.debug { "event=$it" } }
        events.map { it.type } shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
        events.map { it.runId }.distinct() shouldHaveSize 1
    }

    @Test
    fun `reporter isolates listener exception and continues lifecycle`() {
        val events = mutableListOf<GraphIoProgressEventType>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = {
                events += it.type
                if (it.type == GraphIoProgressEventType.PROGRESS) {
                    error("must not escape")
                }
            },
        )

        reporter.started()
        reporter.progress()
        reporter.completed(status = GraphIoStatus.COMPLETED)

        events.forEach { log.debug { "event=$it" } }
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
            listener = { events += it.type },
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

        assertFailsWith<IllegalArgumentException> {
            reporter.progress(vertices = 1L)
        }
        reporter.completed(status = GraphIoStatus.COMPLETED, vertices = 2L)
        reporter.isTerminal().shouldBeTrue()
    }

    @Test
    fun `pre-start cancellation emits only one event with hasStarted false`() {
        val events = mutableListOf<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = { events += it },
        )

        val first = reporter.cancelled()
        val second = reporter.cancelled()

        first?.type shouldBeEqualTo GraphIoProgressEventType.CANCELLED
        first?.hasStarted.shouldBeFalse()
        second.shouldBeNull()

        events.forEach { log.debug { "event=$it" } }
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
            listener = {
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
        reporter.isTerminal().shouldBeTrue()

        events.forEach { log.debug { "event=$it" } }
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
            listener = {
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

        reporter.isTerminal().shouldBeTrue()

        events.forEach { log.debug { "event=$it" } }
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
                {
                    if (it.type == GraphIoProgressEventType.STARTED) throw listenerError
                },
                { events += it.type },
            ),
        )

        assertFailsWith<AssertionError> { reporter.started() } shouldBeEqualTo listenerError

        reporter.isTerminal().shouldBeTrue()
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.FAILED,
        )
    }

    @Test
    fun `default snapshots are resolved while lifecycle lock is held`() {
        val events = ConcurrentLinkedQueue<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = { events += it },
        )
        reporter.started()

        val eventSize = 32
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = List(eventSize) { executor.submit { reporter.progress() } }
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        events.forEach { log.debug { "event=$it" } }
        events.count { it.type == GraphIoProgressEventType.PROGRESS } shouldBeEqualTo eventSize
    }

    @Test
    fun `default snapshots are resolved while lifecycle lock is held in multithreads`() {
        val events = ConcurrentLinkedQueue<GraphIoProgressEvent>()
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = { events += it },
        )
        reporter.started()

        MultithreadingTester()
            .workers(4)
            .rounds(8)
            .add {
                reporter.progress()
            }
            .run()

        events.forEach { log.debug { "event=$it" } }
        events.count { it.type == GraphIoProgressEventType.PROGRESS } shouldBeEqualTo 4 * 8
    }

    @Test
    fun `reentrant listener callback can publish same-run progress`() {
        val events = mutableListOf<GraphIoProgressEventType>()
        lateinit var reporter: GraphIoProgressReporter
        reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = {
                events += it.type
                if (it.type == GraphIoProgressEventType.STARTED) {
                    reporter.progress(vertices = 1L, successfulVertices = 1L, elapsed = 1.millis())
                }
            },
        )

        reporter.started()
        reporter.completed(
            status = GraphIoStatus.COMPLETED,
            vertices = 1L,
            successfulVertices = 1L,
            elapsed = 2.millis(),
        )

        events.forEach { log.debug { "event=$it" } }
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
            listener = { events += it },
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

        events.forEach { log.debug { "event=$it" } }
        events.map { it.runId }.distinct().size shouldBeEqualTo 2
        events.groupBy { it.runId }.values.forEach { runEvents ->
            runEvents.first().type shouldBeEqualTo GraphIoProgressEventType.STARTED
            runEvents.last().type shouldBeEqualTo GraphIoProgressEventType.COMPLETED
        }
    }

    @Test
    fun `multi threading reporters isolate run ids and preserve per-run order`() {
        val events = ConcurrentLinkedQueue<GraphIoProgressEvent>()
        fun newReporter() = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = { events += it },
        )

        val reporters = listOf(newReporter(), newReporter())
        MultithreadingTester()
            .workers(2)
            .rounds(1)
            .add {
                reporters.forEach { reporter ->
                    reporter.run(
                        { Thread.yield() },
                        { reporter.completed(status = GraphIoStatus.COMPLETED) }
                    )
                }
            }
            .run()

        events.forEach { log.debug { "event=$it" } }
        events.map { it.runId }.distinct().size shouldBeEqualTo 2
        events.groupBy { it.runId }.values.forEach { runEvents ->
            runEvents.first().type shouldBeEqualTo GraphIoProgressEventType.STARTED
            runEvents.last().type shouldBeEqualTo GraphIoProgressEventType.COMPLETED
        }
    }
}

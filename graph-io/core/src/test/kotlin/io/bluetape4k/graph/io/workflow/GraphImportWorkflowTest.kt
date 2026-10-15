package io.bluetape4k.graph.io.workflow

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.concurrent.await
import io.bluetape4k.concurrent.awaitTermination
import io.bluetape4k.concurrent.get
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpoint
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class GraphImportWorkflowTest {

    companion object: KLogging()

    private val manifest = GraphImportManifest(
        jobId = "job-1",
        sources = listOf(
            GraphImportSourceSpec("vertices", GraphImportSourceRole.VERTICES, GraphIoFormat.CSV, "sha256:v"),
            GraphImportSourceSpec(
                "edges",
                GraphImportSourceRole.EDGES,
                GraphIoFormat.NDJSON_JACKSON3,
                "sha256:e",
                dependsOn = setOf("vertices"),
            ),
        ),
    )

    @Test
    fun `workflow enforces vertex before edge ordering and persists state`() {
        val store = InMemoryGraphImportJobStateStore()
        val workflow = GraphImportWorkflow(manifest, store)

        workflow.validate().also { report ->
            report.state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
            report.sources.shouldBeEmpty()
            report.checkpoint.shouldBeNull()
            report.elapsed shouldBeEqualTo Duration.ZERO
        }
        workflow.transition(GraphImportWorkflowState.VERTICES_LOADED)
        workflow.transition(GraphImportWorkflowState.EDGES_LOADED)
            .state shouldBeEqualTo GraphImportWorkflowState.EDGES_LOADED

        val report = store.load("job-1").shouldNotBeNull()
        log.debug { "report=$report" }
        report.state shouldBeEqualTo GraphImportWorkflowState.EDGES_LOADED
    }

    @Test
    fun `workflow transition preserves existing report payload`() {
        val store = InMemoryGraphImportJobStateStore()
        val sourceReport = GraphImportSourceReport(
            sourceId = "vertices",
            recordsRead = 10,
            recordsSkipped = 2,
        )
        val checkpoint = GraphImportCheckpoint(
            format = GraphIoFormat.CSV,
            sourceIdentity = "sha256:v",
            phase = GraphImportCheckpointPhase.VERTICES,
            verticesProcessed = 10,
            edgesProcessed = 0,
        )
        val existing = GraphImportWorkflowReport(
            jobId = manifest.jobId,
            state = GraphImportWorkflowState.VALIDATED,
            sources = listOf(sourceReport),
            elapsed = Duration.ofSeconds(3),
            checkpoint = checkpoint,
        )
        store.save(existing)

        val report = GraphImportWorkflow(manifest, store)
            .transition(GraphImportWorkflowState.VERTICES_LOADED)

        log.debug { "report=$report" }

        report.state shouldBeEqualTo GraphImportWorkflowState.VERTICES_LOADED
        report.sources shouldBeEqualTo existing.sources
        report.elapsed shouldBeEqualTo existing.elapsed
        report.checkpoint shouldBeEqualTo existing.checkpoint

        store.load(manifest.jobId) shouldBeEqualTo report
    }

    @Test
    fun `workflow rejects edges before validation`() {
        val workflow = GraphImportWorkflow(manifest, InMemoryGraphImportJobStateStore())
        assertFailsWith<IllegalArgumentException> {
            workflow.transition(GraphImportWorkflowState.EDGES_LOADED)
        }
    }

    @Test
    fun `same job transition is atomic across workflow instances`() {
        val store = BlockingFirstSaveStore()
        GraphImportWorkflow(manifest, store).validate()
        store.armFirstConcurrentSave()

        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<GraphImportWorkflowReport> {
                GraphImportWorkflow(manifest, store).transition(GraphImportWorkflowState.VERTICES_LOADED)
            }
            store.firstSaveEntered.await(5.seconds).shouldBeTrue()

            val secondTaskStarted = CountDownLatch(1)
            val second = executor.submit<GraphImportWorkflowReport> {
                secondTaskStarted.countDown()
                GraphImportWorkflow(manifest, store).transition(GraphImportWorkflowState.VERTICES_LOADED)
            }
            secondTaskStarted.await(5.seconds).shouldBeTrue()
            store.secondSaveEntered.await(1.seconds).shouldBeFalse()
            store.releaseFirstSave.countDown()

            var successes = 0
            var failures = 0
            listOf(first, second).forEach { future ->
                try {
                    future.get(5.seconds)
                    successes++
                } catch (error: ExecutionException) {
                    val cause = error.cause ?: error
                    if (cause is IllegalArgumentException) {
                        failures++
                    } else {
                        throw cause
                    }
                }
            }

            successes shouldBeEqualTo 1
            failures shouldBeEqualTo 1

            val report = store.load(manifest.jobId).shouldNotBeNull()
            log.debug { "report=$report" }
            report.state shouldBeEqualTo GraphImportWorkflowState.VERTICES_LOADED
        } finally {
            store.releaseFirstSave.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5.seconds)
        }
    }

    private class BlockingFirstSaveStore: GraphImportJobStateStore {
        private val delegate = InMemoryGraphImportJobStateStore()
        private val saveCount = AtomicInteger()
        private var blockFirstSave = false

        val firstSaveEntered = CountDownLatch(1)
        val secondSaveEntered = CountDownLatch(1)
        val releaseFirstSave = CountDownLatch(1)

        fun armFirstConcurrentSave() {
            saveCount.set(0)
            blockFirstSave = true
        }

        override fun load(jobId: String): GraphImportWorkflowReport? = delegate.load(jobId)

        override fun save(report: GraphImportWorkflowReport) {
            when (saveCount.incrementAndGet()) {
                1 -> if (blockFirstSave) {
                    firstSaveEntered.countDown()
                    releaseFirstSave.await(5.seconds)
                }
                2 -> if (blockFirstSave) secondSaveEntered.countDown()
            }
            delegate.save(report)
        }
    }
}

package io.bluetape4k.graph.io.workflow

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.concurrent.await
import io.bluetape4k.concurrent.get
import io.bluetape4k.javatimes.millis
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** In-memory store의 job별 직렬성, 병렬성, 취소 안전성을 고정한다. */
class InMemoryGraphImportJobStateStoreConcurrencyTest {

    companion object: KLogging()

    @Test
    fun `different jobs do not block each other`() {
        val store = InMemoryGraphImportJobStateStore()
        store.save(report("job-a"))
        store.save(report("job-b"))

        val firstTransformEntered = CountDownLatch(1)
        val releaseFirstTransform = CountDownLatch(1)
        val secondUpdateFinished = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<GraphImportWorkflowReport> {
                store.update("job-a") { current ->
                    firstTransformEntered.countDown()
                    releaseFirstTransform.await(5.seconds).shouldBeTrue()
                    current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VALIDATED)
                }
            }
            firstTransformEntered.await(5.seconds).shouldBeTrue()

            val second = executor.submit<GraphImportWorkflowReport> {
                try {
                    store.update("job-b") { current ->
                        current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VALIDATED)
                    }
                } finally {
                    secondUpdateFinished.countDown()
                }
            }

            secondUpdateFinished.await(1.seconds).shouldBeTrue()
            releaseFirstTransform.countDown()

            first.get(5.seconds).state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
            second.get(5.seconds).state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
        } finally {
            releaseFirstTransform.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `same job remains serialized`() {
        val store = InMemoryGraphImportJobStateStore()
        store.save(report("job-serial"))

        val firstTransformEntered = CountDownLatch(1)
        val secondTaskStarted = CountDownLatch(1)
        val secondTransformEntered = CountDownLatch(1)
        val releaseFirstTransform = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<GraphImportWorkflowReport> {
                store.update("job-serial") { current ->
                    firstTransformEntered.countDown()
                    releaseFirstTransform.await(5.seconds).shouldBeTrue()
                    current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VALIDATED)
                }
            }
            firstTransformEntered.await(5.seconds).shouldBeTrue()

            val second = executor.submit<GraphImportWorkflowReport> {
                secondTaskStarted.countDown()
                store.update("job-serial") { current ->
                    secondTransformEntered.countDown()
                    val report = current.shouldNotBeNull()
                    require(report.state == GraphImportWorkflowState.DISCOVERED) {
                        "same-job transition observed a newer state"
                    }
                    report.copy(state = GraphImportWorkflowState.VALIDATED)
                }
            }
            secondTaskStarted.await(5.seconds).shouldBeTrue()
            secondTransformEntered.await(250.milliseconds).shouldBeFalse()

            releaseFirstTransform.countDown()
            first.get(5.seconds).state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
            val failure = assertFailsWith<ExecutionException> {
                second.get(5.seconds)
            }
            failure.cause shouldBeInstanceOf IllegalArgumentException::class
            store.load("job-serial")?.state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
        } finally {
            releaseFirstTransform.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `interrupted waiter releases its per-job lock reference`() {
        val store = InMemoryGraphImportJobStateStore()
        store.save(report("job-cancel"))

        val firstTransformEntered = CountDownLatch(1)
        val secondTaskStarted = CountDownLatch(1)
        val secondTransformEntered = CountDownLatch(1)
        val secondTaskFinished = CountDownLatch(1)
        val releaseFirstTransform = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<GraphImportWorkflowReport> {
                store.update("job-cancel") { current ->
                    firstTransformEntered.countDown()
                    releaseFirstTransform.await(5.seconds).shouldBeTrue()
                    current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VALIDATED)
                }
            }
            firstTransformEntered.await(5.seconds).shouldBeTrue()

            val second = executor.submit<GraphImportWorkflowReport> {
                try {
                    secondTaskStarted.countDown()
                    store.update("job-cancel") { current ->
                        secondTransformEntered.countDown()
                        current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VERTICES_LOADED)
                    }
                } finally {
                    secondTaskFinished.countDown()
                }
            }
            secondTaskStarted.await(5.seconds).shouldBeTrue()
            second.cancel(true).shouldBeTrue()
            secondTaskFinished.await(5.seconds).shouldBeTrue()
            secondTransformEntered.await(100.milliseconds).shouldBeFalse()

            releaseFirstTransform.countDown()
            first.get(5.seconds).state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
            store.update("job-cancel") { current ->
                current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VERTICES_LOADED)
            }.state shouldBeEqualTo GraphImportWorkflowState.VERTICES_LOADED
        } finally {
            releaseFirstTransform.countDown()
            executor.shutdownNow()
        }
    }

    private fun report(jobId: String): GraphImportWorkflowReport =
        GraphImportWorkflowReport(
            jobId = jobId,
            state = GraphImportWorkflowState.DISCOVERED,
            elapsed = 1.millis(),
        )
}

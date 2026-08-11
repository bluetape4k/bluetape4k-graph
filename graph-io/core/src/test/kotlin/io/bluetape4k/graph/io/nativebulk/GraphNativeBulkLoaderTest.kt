package io.bluetape4k.graph.io.nativebulk

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

class GraphNativeBulkLoaderTest {

    @Test
    fun `loader validates raw source and executes only validated artifact`() {
        val loader = RecordingLoader()
        val report = loader.load(request(source = RawSource("raw-source")))

        report.outcome shouldBeEqualTo GraphNativeBulkLoadOutcome.COMPLETED
        loader.validatedValue shouldBeEqualTo "validated:raw-source"
        loader.validatorCalls.get() shouldBeEqualTo 1
        loader.closeResourcesCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `unsupported loader fails before validator`() {
        val loader = UnsupportedGraphNativeBulkLoader<RawSource, String>("unsupported")

        val failure = assertFailsWith<GraphNativeBulkLoadException> {
            loader.load(request(source = RawSource("source")))
        }

        failure.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE
    }

    @Test
    fun `listener throwable remains the primary caller failure`() {
        val loader = RecordingLoader()
        val marker = IllegalStateException("listener marker")

        val failure = assertFailsWith<IllegalStateException> {
            loader.load(request(source = RawSource("source"))) {
                throw marker
            }
        }

        (failure === marker).shouldBeTrue()
        loader.validatedValue shouldBeEqualTo null
    }

    @Test
    fun `progress listener receives ordered terminal events on caller thread`() {
        val loader = RecordingLoader()
        val caller = Thread.currentThread()
        val events = mutableListOf<GraphNativeBulkLoadProgress>()

        loader.load(request(source = RawSource("source"))) { progress ->
            (Thread.currentThread() === caller).shouldBeTrue()
            events += progress
        }

        events.map { it.phase } shouldBeEqualTo listOf(
            GraphNativeBulkLoadPhase.PREPARE,
            GraphNativeBulkLoadPhase.LOAD_VERTEX,
            GraphNativeBulkLoadPhase.COMPLETE,
        )
        events.map { it.eventKind } shouldBeEqualTo listOf(
            GraphNativeBulkLoadProgressEventKind.PHASE,
            GraphNativeBulkLoadProgressEventKind.PHASE,
            GraphNativeBulkLoadProgressEventKind.PHASE,
        )
        events.last().outcome shouldBeEqualTo GraphNativeBulkLoadOutcome.COMPLETED
        events.last().processed shouldBeEqualTo 1L
        events.last().succeeded shouldBeEqualTo 1L
        events.last().failed shouldBeEqualTo 0L
    }

    @Test
    fun `progress callback budget violation is a contract failure`() {
        val failure = assertFailsWith<GraphNativeBulkLoadException> {
            OverBudgetLoader().load(request(source = RawSource("source")))
        }

        failure.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION
    }

    @Test
    fun `concurrent load is rejected before a second validator call`() {
        val loader = BlockingLoader()
        val firstFailure = AtomicReference<Throwable?>(null)
        val firstDone = CountDownLatch(1)

        Thread.startVirtualThread {
            try {
                loader.load(request(source = RawSource("first")))
            } catch (failure: Throwable) {
                firstFailure.set(failure)
            } finally {
                firstDone.countDown()
            }
        }

        loader.started.await(5, TimeUnit.SECONDS).shouldBeTrue()
        assertFailsWith<IllegalStateException> {
            loader.load(request(source = RawSource("second")))
        }
        loader.validatorCalls.get() shouldBeEqualTo 1

        loader.release.countDown()
        firstDone.await(5, TimeUnit.SECONDS).shouldBeTrue()
        firstFailure.get().shouldBeNull()
    }

    @Test
    fun `report postcondition violations are contract failures`() {
        val loader = object : RecordingLoader() {
            override fun loadValidated(
                execution: GraphNativeBulkLoadExecution<String>,
                listener: GraphNativeBulkLoadProgressListener?,
            ): GraphNativeBulkLoadReport = GraphNativeBulkLoadReport.create(
                request = GraphNativeBulkLoadRequest(
                    source = "source",
                    sourceKind = GraphNativeBulkLoadSourceKind.FILE,
                    operationName = GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME,
                ),
                capabilities = capabilities,
                outcome = GraphNativeBulkLoadOutcome.COMPLETED,
                processed = 1,
                succeeded = 0,
                failed = 0,
                failures = emptyList(),
                elapsed = Duration.ZERO,
            )
        }

        val failure = assertFailsWith<GraphNativeBulkLoadException> {
            loader.load(request(source = RawSource("source")))
        }

        failure.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION
    }

    @Test
    fun `adapter errors are redacted at the native command boundary`() {
        val failure = assertFailsWith<GraphNativeBulkLoadException> {
            object : RecordingLoader() {
                override fun loadValidated(
                    execution: GraphNativeBulkLoadExecution<String>,
                    listener: GraphNativeBulkLoadProgressListener?,
                ): GraphNativeBulkLoadReport {
                    throw AssertionError("secret-native-error")
                }
            }.load(request(source = RawSource("source")))
        }

        failure.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.NATIVE_COMMAND_FAILED
        (!failure.toString().contains("secret-native-error")).shouldBeTrue()
    }

    @Test
    fun `diagnostics expose only fixed secret-free lifecycle fields`() {
        val diagnostics = CopyOnWriteArrayList<GraphNativeBulkLoadDiagnostic>()
        val loader = RecordingLoader(
            observer = GraphNativeBulkLoadDiagnosticObserver { diagnostics += it },
        )

        loader.load(request(source = RawSource("tenant-secret")))
        loader.close()

        diagnostics.map { it.kind } shouldBeEqualTo listOf(
            GraphNativeBulkLoadDiagnosticKind.STARTED,
            GraphNativeBulkLoadDiagnosticKind.COMPLETED,
            GraphNativeBulkLoadDiagnosticKind.CLOSED,
        )
        diagnostics.forEach { diagnostic ->
            diagnostic.operationName shouldBeEqualTo GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME
            diagnostic.backend shouldBeEqualTo "test-backend"
            (!diagnostic.toString().contains("tenant-secret")).shouldBeTrue()
        }
        loader.close()
        loader.close()
        loader.closeResourcesCalls.get() shouldBeEqualTo 1
    }

    private fun request(source: RawSource): GraphNativeBulkLoadRequest<RawSource> = GraphNativeBulkLoadRequest(
        source = source,
        sourceKind = GraphNativeBulkLoadSourceKind.FILE,
        operationName = GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME,
        timeout = Duration.ofSeconds(5),
        progressInterval = 1,
    )

    private data class RawSource(val value: String)

    private open class RecordingLoader(
        private val counters: Counters = Counters(),
        observer: GraphNativeBulkLoadDiagnosticObserver? = null,
    ) : GraphNativeBulkLoader<RawSource, String>(
        capabilities = capabilities,
        sourceValidator = GraphNativeBulkLoadSourceValidator { request, _, cancellation, _ ->
            counters.validatorCalls.incrementAndGet()
            cancellation.check()
            RecordingValidatedSource("validated:${request.source.value}")
        },
        diagnosticObserver = observer,
    ) {
        val validatorCalls: AtomicInteger
            get() = counters.validatorCalls
        val closeResourcesCalls: AtomicInteger
            get() = counters.closeResourcesCalls
        var validatedValue: String? = null

        override fun loadValidated(
            execution: GraphNativeBulkLoadExecution<String>,
            listener: GraphNativeBulkLoadProgressListener?,
        ): GraphNativeBulkLoadReport {
            listener?.onProgress(
                GraphNativeBulkLoadProgress(
                    phase = GraphNativeBulkLoadPhase.PREPARE,
                    processed = 0,
                    succeeded = 0,
                    failed = 0,
                ),
            )
            validatedValue = execution.source.take()
            listener?.onProgress(
                GraphNativeBulkLoadProgress(
                    phase = GraphNativeBulkLoadPhase.LOAD_VERTEX,
                    processed = 1,
                    succeeded = 1,
                    failed = 0,
                ),
            )
            listener?.onProgress(
                GraphNativeBulkLoadProgress(
                    phase = GraphNativeBulkLoadPhase.COMPLETE,
                    processed = 1,
                    succeeded = 1,
                    failed = 0,
                    outcome = GraphNativeBulkLoadOutcome.COMPLETED,
                ),
            )
            return GraphNativeBulkLoadReport.create(
                request = GraphNativeBulkLoadRequest(
                    source = "validated",
                    sourceKind = GraphNativeBulkLoadSourceKind.FILE,
                    operationName = execution.operationName,
                    timeout = execution.timeout,
                    maxFailureDetails = execution.maxFailureDetails,
                    progressInterval = execution.progressInterval,
                ),
                capabilities = capabilities,
                outcome = GraphNativeBulkLoadOutcome.COMPLETED,
                processed = 1,
                succeeded = 1,
                failed = 0,
                failures = emptyList(),
                elapsed = Duration.ZERO,
            )
        }

        override fun closeResources(deadline: GraphNativeBulkLoadDeadline) {
            counters.closeResourcesCalls.incrementAndGet()
        }

        companion object {
            val capabilities = GraphNativeBulkLoaderCapabilities(
                backend = "test-backend",
                supported = true,
                sourceKinds = setOf(GraphNativeBulkLoadSourceKind.FILE),
                transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.BATCHED,
                failureDetail = GraphNativeBulkLoadFailureDetail.RECORD,
                shutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.BOUNDED,
            )
        }
    }

    private class Counters {
        val validatorCalls = AtomicInteger()
        val closeResourcesCalls = AtomicInteger()
    }

    private class RecordingValidatedSource(
        private val value: String,
    ) : GraphNativeBulkLoadValidatedSource<String>() {
        override fun takeOnce(): String = value

        override fun closeOnce(deadline: GraphNativeBulkLoadDeadline) = Unit
    }

    private class OverBudgetLoader : RecordingLoader() {
        override fun loadValidated(
            execution: GraphNativeBulkLoadExecution<String>,
            listener: GraphNativeBulkLoadProgressListener?,
        ): GraphNativeBulkLoadReport {
            listener?.onProgress(
                GraphNativeBulkLoadProgress(
                    phase = GraphNativeBulkLoadPhase.PREPARE,
                    processed = 0,
                    succeeded = 0,
                    failed = 0,
                ),
            )
            repeat(GraphNativeBulkLoadRequest.MAX_PROGRESS_CALLBACKS.toInt()) { index ->
                listener?.onProgress(
                    GraphNativeBulkLoadProgress(
                        phase = GraphNativeBulkLoadPhase.PREPARE,
                        processed = index + 1L,
                        succeeded = index + 1L,
                        failed = 0,
                        eventKind = GraphNativeBulkLoadProgressEventKind.INTERVAL,
                    ),
                )
            }
            throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.NATIVE_COMMAND_FAILED)
        }
    }

    private class BlockingLoader : RecordingLoader() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun loadValidated(
            execution: GraphNativeBulkLoadExecution<String>,
            listener: GraphNativeBulkLoadProgressListener?,
        ): GraphNativeBulkLoadReport {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            return super.loadValidated(execution, listener)
        }
    }
}

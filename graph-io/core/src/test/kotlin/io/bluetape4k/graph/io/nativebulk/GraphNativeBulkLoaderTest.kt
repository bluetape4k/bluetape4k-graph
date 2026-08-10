package io.bluetape4k.graph.io.nativebulk

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
}

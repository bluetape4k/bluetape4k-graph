package io.bluetape4k.graph.io.nativebulk

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GraphNativeBulkLoadModelsTest {

    @Test
    fun `request has fixed operation label and does not expose source`() {
        val request = request(source = "tenant-secret")

        request.operationName shouldBeEqualTo GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME
        (!request.toString().contains("tenant-secret")).shouldBeTrue()
        (!request.toString().contains(request.operationName)).shouldBeTrue()
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoadRequest(
                source = "source",
                sourceKind = GraphNativeBulkLoadSourceKind.FILE,
                operationName = "tenant-42",
            )
        }
    }

    @Test
    fun `models survive Java serialization`() {
        val original = request(source = "serializable-source")

        roundTrip(original) shouldBeEqualTo original
    }

    @Test
    fun `URI policy is exact and bounded`() {
        val origin = GraphNativeBulkLoadUriOrigin("https", "example.com", 443)
        val policy = GraphNativeBulkLoadSourcePolicy(
            uriAccess = GraphNativeBulkLoadUriAccess.ALLOWLISTED,
            allowedUriOrigins = setOf(origin),
        )

        policy.allowedUriOrigins.size shouldBeEqualTo 1
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoadUriOrigin("https", "example..com")
        }
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoadSourcePolicy(
                uriAccess = GraphNativeBulkLoadUriAccess.ALLOWLISTED,
                allowedUriOrigins = (0..GraphNativeBulkLoadSourcePolicy.MAX_URI_ALLOWLIST_ENTRIES)
                    .map { GraphNativeBulkLoadUriOrigin("https", "host-$it.example") }
                    .toSet(),
            )
        }
    }

    @Test
    fun `supported capabilities require bounded shutdown and matching source policy`() {
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoaderCapabilities(
                backend = "neo4j",
                supported = true,
                sourceKinds = setOf(GraphNativeBulkLoadSourceKind.FILE),
                transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.BATCHED,
                failureDetail = GraphNativeBulkLoadFailureDetail.RECORD,
                shutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.UNKNOWN,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoaderCapabilities(
                backend = "neo4j",
                supported = true,
                sourceKinds = setOf(GraphNativeBulkLoadSourceKind.URI),
                transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.BATCHED,
                failureDetail = GraphNativeBulkLoadFailureDetail.RECORD,
                shutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.BOUNDED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoaderCapabilities(
                backend = "neo4j",
                supported = true,
                sourceKinds = setOf(GraphNativeBulkLoadSourceKind.FILE),
                transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.BATCHED,
                failureDetail = GraphNativeBulkLoadFailureDetail.RECORD,
                shutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.BOUNDED,
                sourcePolicy = GraphNativeBulkLoadSourcePolicy(
                    execution = GraphNativeBulkLoadSourceExecution.BACKEND_SERVER,
                    backendRevalidatesArtifact = false,
                ),
            )
        }
    }

    @Test
    fun `cancellation hook records only the first trigger`() {
        val hookCalls = AtomicInteger()
        val token = GraphNativeBulkLoadCancellationToken(
            startedNanos = System.nanoTime(),
            timeoutNanos = Duration.ofSeconds(1).toNanos(),
        ) { _, _ ->
            hookCalls.incrementAndGet()
            null
        }

        token.request(GraphNativeBulkLoadCancellationReason.CLOSE).shouldBeTrue()
        (!token.request(GraphNativeBulkLoadCancellationReason.TIMEOUT)).shouldBeTrue()
        token.reason shouldBeEqualTo GraphNativeBulkLoadCancellationReason.CLOSE
        hookCalls.get() shouldBeEqualTo 1
    }

    @Test
    fun `report factory enforces outcome and capability invariants`() {
        val request = request(source = "source", maxFailureDetails = 1)
        val capabilities = capabilities()

        GraphNativeBulkLoadReport.create(
            request = request,
            capabilities = capabilities,
            outcome = GraphNativeBulkLoadOutcome.PARTIAL,
            processed = 2,
            succeeded = 1,
            failed = 1,
            failures = listOf(
                GraphNativeBulkLoadFailure(
                    phase = GraphNativeBulkLoadPhase.LOAD_VERTEX,
                    code = GraphNativeBulkLoadFailureCode.NATIVE_COMMAND_FAILED,
                ),
            ),
            elapsed = Duration.ofMillis(1),
        ).outcome shouldBeEqualTo GraphNativeBulkLoadOutcome.PARTIAL

        assertFailsWith<IllegalArgumentException> {
            GraphNativeBulkLoadReport.create(
                request = request,
                capabilities = capabilities,
                outcome = GraphNativeBulkLoadOutcome.COMPLETED,
                processed = 1,
                succeeded = 0,
                failed = 0,
                failures = emptyList(),
                elapsed = Duration.ZERO,
            )
        }

        val atomic = capabilities(transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.ATOMIC)
        assertFailsWith<GraphNativeBulkLoadException> {
            GraphNativeBulkLoadReport.create(
                request = request,
                capabilities = atomic,
                outcome = GraphNativeBulkLoadOutcome.CANCELLED,
                processed = 1,
                succeeded = 1,
                failed = 0,
                failures = emptyList(),
                cancellationReason = GraphNativeBulkLoadCancellationReason.CLOSE,
                elapsed = Duration.ZERO,
            )
        }
    }

    @Test
    fun `validated source is one shot and closes once`() {
        val source = CountingValidatedSource()

        source.take() shouldBeEqualTo "validated"
        assertFailsWith<IllegalStateException> { source.take() }
        source.close()
        source.close()
        source.closeCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `bounded worker redacts assertion errors`() {
        val bounded = runBounded(closeGraceDeadline()) {
            throw AssertionError("secret-error")
        }

        bounded.completed.shouldBeTrue()
        bounded.failure?.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.UNKNOWN
        (!bounded.failure.toString().contains("secret-error")).shouldBeTrue()
    }

    @Test
    fun `closing validated source interrupts an active take`() {
        val source = BlockingValidatedSource()
        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(1)

        Thread.startVirtualThread {
            try {
                source.take()
            } catch (caught: Throwable) {
                failure.set(caught)
            } finally {
                done.countDown()
            }
        }

        source.started.await(5, TimeUnit.SECONDS).shouldBeTrue()
        source.close()
        done.await(5, TimeUnit.SECONDS).shouldBeTrue()
        (failure.get() as GraphNativeBulkLoadException).code shouldBeEqualTo
            GraphNativeBulkLoadFailureCode.CANCELLED
        source.closeCount.get() shouldBeEqualTo 1
        source.observedDeadline.shouldBeTrue()
    }

    @Test
    fun `validation rollback runs registered resources in reverse order`() {
        val closed = mutableListOf<String>()
        val context = GraphNativeBulkLoadValidationContext()
        context.registerRollback(AutoCloseable { closed += "first" })
        context.registerRollback(AutoCloseable { closed += "second" })

        context.rollback(GraphNativeBulkLoadDeadline(System.nanoTime() + Duration.ofSeconds(1).toNanos()))

        closed shouldBeEqualTo listOf("second", "first")
    }

    private fun request(
        source: String,
        maxFailureDetails: Int = GraphNativeBulkLoadRequest.DEFAULT_MAX_FAILURE_DETAILS,
    ): GraphNativeBulkLoadRequest<String> = GraphNativeBulkLoadRequest(
        source = source,
        sourceKind = GraphNativeBulkLoadSourceKind.FILE,
        operationName = GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME,
        maxFailureDetails = maxFailureDetails,
    )

    private fun capabilities(
        transactionGuarantee: GraphNativeBulkLoadTransactionGuarantee =
            GraphNativeBulkLoadTransactionGuarantee.BATCHED,
    ): GraphNativeBulkLoaderCapabilities = GraphNativeBulkLoaderCapabilities(
        backend = "test-backend",
        supported = true,
        sourceKinds = setOf(GraphNativeBulkLoadSourceKind.FILE),
        transactionGuarantee = transactionGuarantee,
        failureDetail = GraphNativeBulkLoadFailureDetail.RECORD,
        shutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.BOUNDED,
    )

    private fun <T> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        @Suppress("UNCHECKED_CAST")
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as T }
    }

    private class CountingValidatedSource : GraphNativeBulkLoadValidatedSource<String>() {
        val closeCount = AtomicInteger()

        override fun takeOnce(): String = "validated"

        override fun closeOnce(deadline: GraphNativeBulkLoadDeadline) {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingValidatedSource : GraphNativeBulkLoadValidatedSource<String>() {
        val started = CountDownLatch(1)
        val closeCount = AtomicInteger()
        var observedDeadline = false

        override fun takeOnce(deadline: GraphNativeBulkLoadDeadline): String {
            observedDeadline = deadline.remainingNanos() > 0L
            started.countDown()
            CountDownLatch(1).await()
            return "unreachable"
        }

        override fun closeOnce(deadline: GraphNativeBulkLoadDeadline) {
            closeCount.incrementAndGet()
        }
    }
}

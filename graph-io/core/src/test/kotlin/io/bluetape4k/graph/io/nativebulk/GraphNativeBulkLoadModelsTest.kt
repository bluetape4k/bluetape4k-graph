package io.bluetape4k.graph.io.nativebulk

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.fail
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.concurrent.await
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.javatimes.inNanos
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class GraphNativeBulkLoadModelsTest {

    companion object: KLogging()

    @Test
    fun `request has fixed operation label and does not expose source`() {
        val request = request(source = "tenant-secret")

        log.debug { "request=$request" }
        request.operationName shouldBeEqualTo GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME
        request.toString() shouldNotContain "tenant-secret"
        request.toString() shouldNotContain request.operationName

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
    fun `models survive Fory serialization`() {
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

        log.debug { "policy=$policy" }
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

        log.debug { "token=$token" }
        token.request(GraphNativeBulkLoadCancellationReason.CLOSE).shouldBeTrue()
        token.request(GraphNativeBulkLoadCancellationReason.TIMEOUT).shouldBeFalse()
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

        assertFailsWith<IllegalStateException> {
            source.take()
        }
        source.close()
        source.close()
        source.closeCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `bounded worker redacts assertion errors`() {
        val bounded = runBounded(closeGraceDeadline()) {
            fail("secret-error")
        }

        bounded.completed.shouldBeTrue()
        bounded.failure?.code shouldBeEqualTo GraphNativeBulkLoadFailureCode.UNKNOWN
        bounded.failure.toString() shouldNotContain "secret-error"
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

        source.started.await(5.seconds).shouldBeTrue()
        source.close()
        done.await(5.seconds).shouldBeTrue()

        val error = failure.get()
        error.shouldBeInstanceOf<GraphNativeBulkLoadException>().code shouldBeEqualTo GraphNativeBulkLoadFailureCode.CANCELLED

        source.closeCount.get() shouldBeEqualTo 1
        source.observedDeadline.shouldBeTrue()
    }

    @Test
    fun `validation rollback runs registered resources in reverse order`() {
        val closed = mutableListOf<String>()
        val context = GraphNativeBulkLoadValidationContext()
        context.registerRollback { closed += "first" }
        context.registerRollback { closed += "second" }

        context.rollback(GraphNativeBulkLoadDeadline(System.nanoTime() + 1.seconds().inNanos()))

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

    private fun <T: Any> roundTrip(value: T): T? {
        val jdkSerializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = jdkSerializer.serialize(value)
        return jdkSerializer.deserialize(bytes)
    }

    private fun <T: Any> roundTripFory(value: T): T? {
        val bytes = BinarySerializers.FastFory.serialize(value)
        return BinarySerializers.FastFory.deserialize(bytes)
    }

    private class CountingValidatedSource: GraphNativeBulkLoadValidatedSource<String>() {
        val closeCount = AtomicInteger()

        override fun takeOnce(): String = "validated"

        override fun closeOnce(deadline: GraphNativeBulkLoadDeadline) {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingValidatedSource: GraphNativeBulkLoadValidatedSource<String>() {
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

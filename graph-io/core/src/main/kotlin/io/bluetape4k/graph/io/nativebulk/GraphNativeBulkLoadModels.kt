package io.bluetape4k.graph.io.nativebulk

import java.io.Serializable
import java.time.Duration
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun <T> immutableSet(values: Set<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))

internal fun <T> immutableList(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun String.requireLogSafe(field: String, maxLength: Int): String {
    require(length in 1..maxLength) { "$field must be a non-empty bounded identifier" }
    require(matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))) {
        "$field contains unsupported characters"
    }
    return this
}

internal fun saturatingAdd(base: Long, delta: Long): Long = when {
    delta > 0L && base > Long.MAX_VALUE - delta -> Long.MAX_VALUE
    delta < 0L && base < Long.MIN_VALUE - delta -> Long.MIN_VALUE
    else -> base + delta
}

internal fun saturatingSubtract(minuend: Long, subtrahend: Long): Long = when {
    subtrahend < 0L && minuend > Long.MAX_VALUE + subtrahend -> Long.MAX_VALUE
    subtrahend > 0L && minuend < Long.MIN_VALUE + subtrahend -> Long.MIN_VALUE
    else -> minuend - subtrahend
}

internal fun earliestDeadline(
    first: GraphNativeBulkLoadDeadline,
    second: GraphNativeBulkLoadDeadline,
): GraphNativeBulkLoadDeadline =
    GraphNativeBulkLoadDeadline(minOf(first.deadlineNanos, second.deadlineNanos))

enum class GraphNativeBulkLoadSourceKind { FILE, DIRECTORY, URI }

enum class GraphNativeBulkLoadTransactionGuarantee {
    ATOMIC, BATCHED, NONE, UNKNOWN
}

enum class GraphNativeBulkLoadFailureDetail {
    RECORD, BATCH, COMMAND, SUMMARY, NONE
}

enum class GraphNativeBulkLoadPhase {
    PREPARE, LOAD_VERTEX, LOAD_EDGE, VERIFY, COMPLETE
}

enum class GraphNativeBulkLoadOutcome {
    COMPLETED, PARTIAL, FAILED, CANCELLED
}

enum class GraphNativeBulkLoadFailureCode(val publicMessage: String) {
    INVALID_REQUEST("native bulk load request is invalid"),
    SOURCE_REJECTED("native bulk load source was rejected"),
    UNSUPPORTED_SOURCE("native bulk load source is unsupported"),
    NATIVE_COMMAND_FAILED("native bulk load command failed"),
    PARTIAL_RESULT("native bulk load completed with partial failures"),
    TIMEOUT("native bulk load timed out"),
    CANCELLED("native bulk load was cancelled"),
    CONTRACT_VIOLATION("native bulk load contract was violated"),
    UNKNOWN("native bulk load failed"),
}

enum class GraphNativeBulkLoadCancellationReason {
    TIMEOUT, INTERRUPT, CLOSE, LISTENER_FAILURE
}

open class GraphNativeBulkLoadException(
    val code: GraphNativeBulkLoadFailureCode,
) : RuntimeException(code.publicMessage)

class GraphNativeBulkLoadCancellationException(
    val reason: GraphNativeBulkLoadCancellationReason,
) : GraphNativeBulkLoadException(
    if (reason == GraphNativeBulkLoadCancellationReason.TIMEOUT) {
        GraphNativeBulkLoadFailureCode.TIMEOUT
    } else {
        GraphNativeBulkLoadFailureCode.CANCELLED
    },
)

internal fun redactNativeBulkLoadFailure(
    failure: GraphNativeBulkLoadException,
): GraphNativeBulkLoadException =
    if (failure is GraphNativeBulkLoadCancellationException) {
        GraphNativeBulkLoadCancellationException(failure.reason)
    } else {
        GraphNativeBulkLoadException(failure.code)
    }


enum class GraphNativeBulkLoadUriAccess {
    DENIED, ALLOWLISTED
}

enum class GraphNativeBulkLoadSourceExecution {
    CALLER_JVM, BACKEND_SERVER
}

enum class GraphNativeBulkLoadShutdownGuarantee {
    BOUNDED, UNKNOWN
}

data class GraphNativeBulkLoadDeadline(
    val deadlineNanos: Long,
) : Serializable {
    fun remainingNanos(nowNanos: Long = System.nanoTime()): Long =
        saturatingSubtract(deadlineNanos, nowNanos).coerceAtLeast(0L)

    val isExpired: Boolean
        get() = remainingNanos() == 0L

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Runs adapter-owned shutdown/observer work without allowing an unbounded block on the caller.
 * A timeout returns immediately, but terminal state publication is registered on the worker's
 * completion future and therefore cannot precede the actual action completion.
 */
internal class GraphNativeBulkLoadBoundedCall internal constructor(
    val failure: GraphNativeBulkLoadException?,
    private val completion: CompletableFuture<GraphNativeBulkLoadException?>,
) {
    val completed: Boolean
        get() = completion.isDone

    fun onCompletion(action: (GraphNativeBulkLoadException?) -> Unit) {
        completion.whenComplete { failure, _ ->
            try {
                action(failure)
            } catch (_: Exception) {
                // Late terminal publication is best effort and never leaks worker failures.
            }
        }
    }
}

internal fun runBounded(
    deadline: GraphNativeBulkLoadDeadline,
    action: () -> Unit,
): GraphNativeBulkLoadBoundedCall {
    val completion = CompletableFuture<GraphNativeBulkLoadException?>()
    val worker = Thread.startVirtualThread {
        var failure: GraphNativeBulkLoadException? = null
        try {
            action()
        } catch (caught: GraphNativeBulkLoadException) {
            failure = redactNativeBulkLoadFailure(caught)
        } catch (_: InterruptedException) {
            failure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
        } catch (_: Exception) {
            failure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
        } finally {
            completion.complete(failure)
        }
    }
    var interrupted = false
    return try {
        while (true) {
            val remaining = deadline.remainingNanos()
            if (remaining == 0L) {
                worker.interrupt()
                return GraphNativeBulkLoadBoundedCall(
                    GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT),
                    completion,
                )
            }
            try {
                return GraphNativeBulkLoadBoundedCall(
                    completion.get(remaining, TimeUnit.NANOSECONDS),
                    completion,
                )
            } catch (_: InterruptedException) {
                interrupted = true
                worker.interrupt()
            } catch (_: TimeoutException) {
                worker.interrupt()
                return GraphNativeBulkLoadBoundedCall(
                    GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT),
                    completion,
                )
            }
        }
        error("bounded call did not return")
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

data class GraphNativeBulkLoadUriOrigin(
    val scheme: String,
    val host: String,
    val port: Int? = null,
) : Serializable {
    init {
        require(scheme.matches(Regex("[a-z][a-z0-9+.-]{0,31}"))) {
            "URI scheme must be normalized lowercase bounded identifier"
        }
        require(host.length in 1..253 && host == host.lowercase()) {
            "URI host must be normalized lowercase exact host"
        }
        require(host.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))) {
            "URI host contains unsupported characters"
        }
        require(!host.contains("..") && !host.contains('*')) {
            "URI host must not contain wildcards or empty labels"
        }
        require(port == null || port in 1..65_535) { "URI port is out of range" }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class GraphNativeBulkLoadRequest<R : Any>(
    val source: R,
    val sourceKind: GraphNativeBulkLoadSourceKind,
    /** Secret-free fixed operation label; never use a tenant/request identifier. */
    val operationName: String,
    val timeout: Duration? = null,
    val maxFailureDetails: Int = DEFAULT_MAX_FAILURE_DETAILS,
    val progressInterval: Long = DEFAULT_PROGRESS_INTERVAL,
) : Serializable {
    init {
        operationName.requireLogSafe("operationName", MAX_OPERATION_NAME_LENGTH)
        require(operationName == REQUIRED_OPERATION_NAME) {
            "operationName must be the fixed native-bulk-load operation label"
        }
        timeout?.let {
            require(!it.isNegative && !it.isZero) { "timeout must be positive" }
            require(it <= MAX_TIMEOUT) { "timeout exceeds the supported finite maximum" }
        }
        require(maxFailureDetails in 0..MAX_FAILURE_DETAILS) {
            "maxFailureDetails must be between 0 and $MAX_FAILURE_DETAILS"
        }
        require(progressInterval > 0) { "progressInterval must be > 0" }
    }

    companion object {
        const val DEFAULT_MAX_FAILURE_DETAILS: Int = 100
        const val MAX_FAILURE_DETAILS: Int = 1_024
        const val DEFAULT_PROGRESS_INTERVAL: Long = 1_000
        const val MAX_PROGRESS_CALLBACKS: Long = 1_024
        const val MAX_OPERATION_NAME_LENGTH: Int = 128
        const val REQUIRED_OPERATION_NAME: String = "native-bulk-load"
        val DEFAULT_TIMEOUT: Duration = Duration.ofHours(1)
        val MAX_TIMEOUT: Duration = Duration.ofDays(365)
        val DEFAULT_CLOSE_GRACE: Duration = Duration.ofSeconds(30)
        private const val serialVersionUID: Long = 1L
    }

    override fun toString(): String =
        "GraphNativeBulkLoadRequest(sourceKind=$sourceKind, " +
            "timeout=$timeout, maxFailureDetails=$maxFailureDetails, progressInterval=$progressInterval)"

}

class GraphNativeBulkLoaderCapabilities(
    backend: String,
    val supported: Boolean,
    sourceKinds: Set<GraphNativeBulkLoadSourceKind>,
    val transactionGuarantee: GraphNativeBulkLoadTransactionGuarantee,
    val failureDetail: GraphNativeBulkLoadFailureDetail,
    val shutdownGuarantee: GraphNativeBulkLoadShutdownGuarantee = GraphNativeBulkLoadShutdownGuarantee.UNKNOWN,
    sourcePolicy: GraphNativeBulkLoadSourcePolicy = GraphNativeBulkLoadSourcePolicy(),
) : Serializable {
    val backend: String = backend.requireLogSafe("backend", MAX_BACKEND_LENGTH)
    val sourceKinds: Set<GraphNativeBulkLoadSourceKind> = immutableSet(sourceKinds)
    val sourcePolicy: GraphNativeBulkLoadSourcePolicy = sourcePolicy

    init {
        if (!supported) {
            require(sourceKinds.isEmpty()) { "unsupported loader must not advertise source kinds" }
            require(transactionGuarantee == GraphNativeBulkLoadTransactionGuarantee.UNKNOWN) {
                "unsupported loader must not advertise transaction guarantees"
            }
            require(failureDetail == GraphNativeBulkLoadFailureDetail.NONE) {
                "unsupported loader must not advertise failure detail"
            }
            require(shutdownGuarantee == GraphNativeBulkLoadShutdownGuarantee.UNKNOWN) {
                "unsupported loader must not advertise shutdown guarantee"
            }
            require(sourcePolicy.isDeniedByDefault()) {
                "unsupported loader must use the default denied source policy"
            }
        } else {
            require(sourceKinds.isNotEmpty()) { "supported loader must advertise source kinds" }
            require(shutdownGuarantee == GraphNativeBulkLoadShutdownGuarantee.BOUNDED) {
                "supported loader requires a bounded shutdown guarantee"
            }
            if (GraphNativeBulkLoadSourceKind.URI !in sourceKinds) {
                require(sourcePolicy.uriAccess == GraphNativeBulkLoadUriAccess.DENIED) {
                    "URI policy requires URI source support"
                }
            } else {
                require(sourcePolicy.uriAccess == GraphNativeBulkLoadUriAccess.ALLOWLISTED) {
                    "advertised URI source support requires an allowlisted URI policy"
                }
            }
            if (sourcePolicy.execution == GraphNativeBulkLoadSourceExecution.BACKEND_SERVER) {
                if (GraphNativeBulkLoadSourceKind.URI in sourceKinds) {
                    require(sourcePolicy.backendRevalidatesOrigin) {
                        "backend-server URI execution requires backend origin revalidation"
                    }
                }
                if (GraphNativeBulkLoadSourceKind.FILE in sourceKinds ||
                    GraphNativeBulkLoadSourceKind.DIRECTORY in sourceKinds
                ) {
                    require(sourcePolicy.backendRevalidatesArtifact) {
                        "backend-server file execution requires artifact revalidation"
                    }
                }
            }
            if (GraphNativeBulkLoadSourceKind.FILE in sourceKinds ||
                GraphNativeBulkLoadSourceKind.DIRECTORY in sourceKinds ||
                sourcePolicy.execution == GraphNativeBulkLoadSourceExecution.BACKEND_SERVER
            ) {
                require(sourcePolicy.requiresApprovedStagingRoot) {
                    "file, directory, and backend-server sources require an approved staging root"
                }
            }
        }
    }

    companion object {
        const val MAX_BACKEND_LENGTH: Int = 64
        private const val serialVersionUID: Long = 1L
    }
}

class GraphNativeBulkLoadSourcePolicy(
    val uriAccess: GraphNativeBulkLoadUriAccess = GraphNativeBulkLoadUriAccess.DENIED,
    allowedUriOrigins: Set<GraphNativeBulkLoadUriOrigin> = emptySet(),
    val execution: GraphNativeBulkLoadSourceExecution = GraphNativeBulkLoadSourceExecution.CALLER_JVM,
    val allowPrivateNetworks: Boolean = false,
    val allowRedirects: Boolean = false,
    val maxRedirectHops: Int = 0,
    val maxUriLength: Int = DEFAULT_MAX_URI_LENGTH,
    val allowCredentials: Boolean = false,
    val requiresApprovedStagingRoot: Boolean = true,
    val backendRevalidatesOrigin: Boolean = false,
    val backendRevalidatesArtifact: Boolean = false,
) : Serializable {
    companion object {
        const val MAX_URI_ALLOWLIST_ENTRIES: Int = 32
        const val MAX_URI_ALLOWLIST_BYTES: Int = 4_096
        const val DEFAULT_MAX_URI_LENGTH: Int = 4_096
        const val MAX_URI_LENGTH: Int = 8_192
        const val MAX_REDIRECT_HOPS: Int = 5
        private const val serialVersionUID: Long = 1L
    }

    val allowedUriOrigins: Set<GraphNativeBulkLoadUriOrigin> = immutableSet(allowedUriOrigins)

    init {
        require(!allowCredentials) { "native bulk load source credentials are never allowed" }
        require(allowedUriOrigins.size <= MAX_URI_ALLOWLIST_ENTRIES) {
            "too many URI origins"
        }
        require(allowedUriOrigins.sumOf { it.toString().length } <= MAX_URI_ALLOWLIST_BYTES) {
            "URI allowlist is too large"
        }
        require(maxUriLength in 1..MAX_URI_LENGTH) { "maxUriLength is out of range" }
        require(maxRedirectHops in 0..MAX_REDIRECT_HOPS) { "maxRedirectHops is out of range" }
        require(!allowRedirects || maxRedirectHops > 0) {
            "redirects require a positive hop limit"
        }
        require(allowRedirects || maxRedirectHops == 0) {
            "a redirect hop limit requires redirects"
        }
        if (uriAccess == GraphNativeBulkLoadUriAccess.ALLOWLISTED) {
            require(allowedUriOrigins.isNotEmpty()) { "allowlisted URI access requires exact origins" }
        } else {
            require(allowedUriOrigins.isEmpty()) { "denied URI access must not advertise origins" }
            require(!allowRedirects) { "denied URI access must not allow redirects" }
            require(!allowPrivateNetworks) { "denied URI access must not allow private networks" }
            require(!backendRevalidatesOrigin) { "denied URI access must not advertise origin validation" }
        }
    }

    fun isDeniedByDefault(): Boolean =
        uriAccess == GraphNativeBulkLoadUriAccess.DENIED &&
            allowedUriOrigins.isEmpty() &&
            execution == GraphNativeBulkLoadSourceExecution.CALLER_JVM &&
            !allowPrivateNetworks &&
            !allowRedirects &&
            maxRedirectHops == 0 &&
            maxUriLength == DEFAULT_MAX_URI_LENGTH &&
            !allowCredentials &&
            requiresApprovedStagingRoot &&
            !backendRevalidatesOrigin &&
            !backendRevalidatesArtifact
}

data class GraphNativeBulkLoadProgress(
    val phase: GraphNativeBulkLoadPhase,
    val processed: Long,
    val succeeded: Long,
    val failed: Long,
    val outcome: GraphNativeBulkLoadOutcome? = null,
    val eventKind: GraphNativeBulkLoadProgressEventKind = GraphNativeBulkLoadProgressEventKind.PHASE,
) : Serializable {
    init {
        require(processed >= 0 && succeeded >= 0 && failed >= 0) { "progress counts must be >= 0" }
        require(succeeded <= processed && failed <= processed - succeeded) {
            "succeeded + failed must be <= processed"
        }
        require((phase == GraphNativeBulkLoadPhase.COMPLETE) == (outcome != null)) {
            "only COMPLETE progress may carry a terminal outcome"
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class GraphNativeBulkLoadProgressEventKind {
    PHASE, INTERVAL
}

data class GraphNativeBulkLoadFailure(
    val phase: GraphNativeBulkLoadPhase,
    val code: GraphNativeBulkLoadFailureCode,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

val GraphNativeBulkLoadFailure.message: String
    get() = code.publicMessage


internal fun mergeRedactedFailure(
    primary: GraphNativeBulkLoadException?,
    additional: GraphNativeBulkLoadException,
): GraphNativeBulkLoadException {
    if (primary == null) return additional
    primary.addSuppressed(additional)
    return primary
}

internal fun contractRequire(value: Boolean, _lazyMessage: () -> String) {
    if (!value) throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION)
}

class GraphNativeBulkLoadReport private constructor(
    val operationName: String,
    val outcome: GraphNativeBulkLoadOutcome,
    val processed: Long,
    val succeeded: Long,
    val failed: Long,
    failures: List<GraphNativeBulkLoadFailure>,
    val failureDetailsLimit: Int,
    val omittedFailureDetails: Long,
    val cancellationReason: GraphNativeBulkLoadCancellationReason?,
    val elapsed: Duration,
) : Serializable {
    val failures: List<GraphNativeBulkLoadFailure> = immutableList(failures)

    companion object {
        fun create(
            request: GraphNativeBulkLoadRequest<*>,
            capabilities: GraphNativeBulkLoaderCapabilities,
            outcome: GraphNativeBulkLoadOutcome,
            processed: Long,
            succeeded: Long,
            failed: Long,
            failures: List<GraphNativeBulkLoadFailure>,
            omittedFailureDetails: Long = 0,
            cancellationReason: GraphNativeBulkLoadCancellationReason? = null,
            elapsed: Duration,
        ): GraphNativeBulkLoadReport {
            val operationName = request.operationName
            val failureDetailsLimit = request.maxFailureDetails
            require(processed >= 0 && succeeded >= 0 && failed >= 0) { "load counts must be >= 0" }
            require(succeeded <= processed && failed <= processed - succeeded) {
                "succeeded + failed must be <= processed"
            }
            require(failureDetailsLimit in 0..GraphNativeBulkLoadRequest.MAX_FAILURE_DETAILS) {
                "failureDetailsLimit is out of range"
            }
            require(failures.size <= failureDetailsLimit) { "failure details exceed the configured limit" }
            require(omittedFailureDetails >= 0) { "omittedFailureDetails must be >= 0" }
            require(outcome == GraphNativeBulkLoadOutcome.CANCELLED == (cancellationReason != null)) {
                "only CANCELLED outcome may carry a cancellation reason"
            }
            require(elapsed >= Duration.ZERO) { "elapsed must be >= 0" }
            when (outcome) {
                GraphNativeBulkLoadOutcome.COMPLETED -> {
                    require(succeeded == processed && failed == 0L && failures.isEmpty() && omittedFailureDetails == 0L) {
                        "COMPLETED outcome must not contain failures"
                    }
                }
                GraphNativeBulkLoadOutcome.PARTIAL -> require(
                    succeeded > 0 && failed > 0 &&
                        (omittedFailureDetails > 0 || failures.isNotEmpty())
                ) {
                    "PARTIAL outcome requires durable successes and failed records"
                }
                GraphNativeBulkLoadOutcome.FAILED -> require(
                    failed > 0 || omittedFailureDetails > 0 || failures.isNotEmpty()
                ) {
                    "FAILED outcome requires failed records, omitted details, or an operation failure detail"
                }
                GraphNativeBulkLoadOutcome.CANCELLED -> Unit
            }
            val report = GraphNativeBulkLoadReport(
                operationName,
                outcome,
                processed,
                succeeded,
                failed,
                failures.toList(),
                failureDetailsLimit,
                omittedFailureDetails,
                cancellationReason,
                elapsed,
            )
            return report.requireCompatible(request, capabilities)
        }

        private const val serialVersionUID: Long = 1L
    }

    fun requireCompatible(
        request: GraphNativeBulkLoadRequest<*>,
        capabilities: GraphNativeBulkLoaderCapabilities,
    ): GraphNativeBulkLoadReport {
        contractRequire(operationName == request.operationName) { "report operationName does not match request" }
        contractRequire(failureDetailsLimit == request.maxFailureDetails) {
            "report failureDetailsLimit does not match request"
        }
        if (capabilities.transactionGuarantee == GraphNativeBulkLoadTransactionGuarantee.ATOMIC) {
            contractRequire(outcome != GraphNativeBulkLoadOutcome.PARTIAL) {
                "ATOMIC capability cannot return PARTIAL outcome"
            }
            if (outcome != GraphNativeBulkLoadOutcome.COMPLETED) {
                contractRequire(succeeded == 0L && failed == 0L) {
                    "ATOMIC non-completed outcomes must report zero durable counts"
                }
            }
        }
        if (capabilities.failureDetail == GraphNativeBulkLoadFailureDetail.NONE) {
            contractRequire(failures.isEmpty()) { "failureDetail NONE cannot return failure details" }
        }
        return this
    }

}

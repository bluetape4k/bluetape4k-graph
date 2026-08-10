# Issue #312 backend-native bulk loader SPI 설계

> 관련 이슈: [#312](https://github.com/bluetape4k/bluetape4k-graph/issues/312)
> 선행 조사: [Issue #234 native bulk loaders](../research/2026-06-26-issue-234-native-bulk-loaders.md)

## 문제와 목표

현재 `graph-io`의 벌크 import는 `GraphBulkImporter`가 레코드를 읽어
`GraphOperations`에 반복해서 쓰는 backend-neutral 경로만 제공한다. Neo4j,
Memgraph, Apache AGE, FalkorDB는 각각 서버 또는 드라이버가 제공하는 native
bulk-loading 경로가 있지만, 파일 staging, URI 매핑, 트랜잭션 경계, 실패 집계
계약이 서로 다르다.

이 이슈의 목표는 실제 backend adapter를 추가하는 것이 아니라, backend 모듈이
향후 자신의 native loader를 안전하게 제공할 수 있는 `graph-io-core` SPI와
불변 값 모델을 정의하는 것이다. 구현 결과는 backend-neutral importer와
혼동되지 않으며, 지원하지 않는 backend는 명시적으로 거절할 수 있어야 한다.

## 근거와 현재 상태

- #234 조사 문서는 native loader 구현을 공통 SPI가 정의될 때까지 보류하도록
  권고한다.
- Neo4j `LOAD CSV`, Memgraph `LOAD CSV`, AGE file loader, FalkorDB
  `GRAPH.BULK`는 source staging과 transaction/failure semantics가 다르다.
- TinkerGraph/TinkerPop에는 이 SPI를 적용하지 않는다.
- 기준선은 `:bluetape4k-graph-io-core:test` 82개 통과다.
- 이 이슈는 파일을 열거나 URI를 dereference하는 adapter를 만들지 않는다.
  따라서 source trust policy는 공통 SPI가 지켜야 할 경계로 문서화하고,
  실제 허용 목록은 후속 backend adapter가 선언한다.
- 구현 기준선은 저장소의 현재 toolchain인 Kotlin 2.4, JDK/JVM 25,
  Gradle 9.7.0이다. 다른 버전 조합을 전제로 한 compile 근거는 사용하지
  않는다.
- graph-io의 기존 value/report 계약에 맞춰 public data model은
  `java.io.Serializable`과 고정 `serialVersionUID`를 갖는다. `R`은 caller-owned
  opaque source이므로 wire serialization 자체는 이 SPI의 기능이 아니며,
  직렬화 경계를 넘길 때는 호출자가 `R`의 직렬화 가능성을 보장해야 한다.

## 범위

### 포함

- `graph-io-core`의 backend-native bulk loader SPI.
- source kind, transaction guarantee, failure detail, phase를 나타내는 작은
  enum과 불변 request/capabilities/progress/failure/report 모델.
- 동기 `load` 호출과 선택적 progress listener.
- 지원하지 않는 backend를 표현하는 명시적 `Unsupported...` 구현.
- lifecycle, progress, failure mapping, unsupported behavior에 대한 단위 테스트.
- `README.md`, `README.ko.md`의 API 안내와 backend adapter 비포함 경계.

### 제외

- Neo4j, Memgraph, AGE, FalkorDB, TinkerPop의 실제 adapter.
- 서버별 명령 실행, 파일 업로드/staging, 인증·권한 설정, Testcontainers
  backend 통합 테스트.
- 새로운 Gradle module/dependency, 기존 `GraphBulkImporter`의 동작 변경,
  `GraphOperations` 또는 `GraphCore` API 변경.
- coroutine/virtual-thread 별도 adapter. 필요하면 동일한 SPI를 감싸는 후속
  이슈에서 설계한다.

## 제안 API

패키지는 `io.bluetape4k.graph.io.nativebulk`로 둔다. source 자체의 타입은
backend가 결정하므로 SPI는 제네릭으로 유지한다.

```kotlin
import java.io.Serializable
import java.time.Duration
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

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

class GraphNativeBulkLoadCancellationToken internal constructor(
    val startedNanos: Long,
    val timeoutNanos: Long,
    private val cancellationHook: (
        GraphNativeBulkLoadCancellationReason,
        GraphNativeBulkLoadDeadline,
    ) -> GraphNativeBulkLoadException?,
) {
    private val deadlineNanos: Long = saturatingAdd(startedNanos, timeoutNanos)
    private val requestedReason = AtomicReference<GraphNativeBulkLoadCancellationReason?>(null)
    private val hookInvoked = AtomicBoolean(false)
    private val hookFailure = AtomicReference<GraphNativeBulkLoadException?>(null)

    val reason: GraphNativeBulkLoadCancellationReason?
        get() = requestedReason.get()

    val isCancellationRequested: Boolean
        get() = requestedReason.get() != null

    fun remainingNanos(): Long {
        return saturatingSubtract(deadlineNanos, System.nanoTime()).coerceAtLeast(0L)
    }

    fun deadline(): GraphNativeBulkLoadDeadline = GraphNativeBulkLoadDeadline(deadlineNanos)

    /** Atomically records the first reason and invokes the bounded hook exactly once. */
    fun request(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        ),
    ): Boolean {
        if (!requestedReason.compareAndSet(null, reason)) return false
        if (hookInvoked.compareAndSet(false, true)) {
            cancellationHook(reason, deadline)?.let { hookFailure.compareAndSet(null, it) }
        }
        return true
    }

    internal fun cancellationHookFailure(): GraphNativeBulkLoadException? = hookFailure.get()

    fun check() {
        val loadDeadline = deadline()
        if (Thread.currentThread().isInterrupted) {
            request(GraphNativeBulkLoadCancellationReason.INTERRUPT, loadDeadline)
        }
        if (remainingNanos() == 0L) {
            request(GraphNativeBulkLoadCancellationReason.TIMEOUT, loadDeadline)
        }
        requestedReason.get()?.let { reason ->
            throw GraphNativeBulkLoadCancellationException(reason)
        }
    }
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

abstract class GraphNativeBulkLoadValidatedSource<V : Any> : AutoCloseable {
    private enum class State { OPEN, CLOSING, CLOSED }

    private val lifecycleLock = ReentrantLock()
    private val lifecycleChanged: Condition = lifecycleLock.newCondition()
    private var state = State.OPEN
    private var taken = false
    private var takeInFlight = false
    private val closeStarted = AtomicBoolean(false)

    /** Validation 결과에 결합된 canonical/pinned artifact를 단 한 번만 소비한다. */
    final fun take(): V {
        lifecycleLock.lock()
        try {
            check(state == State.OPEN && !taken) { "validated source is not available" }
            taken = true
            takeInFlight = true
        } finally {
            lifecycleLock.unlock()
        }
        var value: V? = null
        var primaryFailure: Throwable? = null
        var deferredCleanupOwner = false
        try {
            value = takeOnce()
        } catch (failure: Throwable) {
            primaryFailure = failure
        } finally {
            lifecycleLock.lock()
            try {
                takeInFlight = false
                if (state == State.CLOSING && closeStarted.compareAndSet(false, true)) {
                    deferredCleanupOwner = true
                }
                lifecycleChanged.signalAll()
            } finally {
                lifecycleLock.unlock()
            }
        }
        if (deferredCleanupOwner) {
            val deferredDeadline = GraphNativeBulkLoadDeadline(
                saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
            )
            val deferredCall = runBounded(deferredDeadline) { closeOnce(deferredDeadline) }
            if (deferredCall.completed) publishClosed()
            else deferredCall.onCompletion { publishClosed() }
            deferredCall.failure?.let { boundedFailure ->
                if (primaryFailure == null) primaryFailure = boundedFailure
                else primaryFailure?.addSuppressed(boundedFailure)
            }
        }
        primaryFailure?.let { throw it }
        check(value != null) { "validated source did not produce an artifact" }
        return value
    }

    protected abstract fun takeOnce(): V

    final override fun close() {
        val deadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        )
        var interrupted = Thread.interrupted()
        var ownsClose = false
        var timedOut = false
        lifecycleLock.lock()
        try {
            when (state) {
                State.OPEN -> state = State.CLOSING
                State.CLOSING -> Unit
                State.CLOSED -> Unit
            }
            while (takeInFlight) {
                val remaining = deadline.remainingNanos()
                if (remaining == 0L) {
                    timedOut = true
                    break
                }
                try {
                    lifecycleChanged.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            if (!timedOut && state == State.CLOSING && closeStarted.compareAndSet(false, true)) {
                ownsClose = true
            } else if (!timedOut) {
                while (state != State.CLOSED && closeStarted.get()) {
                    val remaining = deadline.remainingNanos()
                    if (remaining == 0L) {
                        timedOut = true
                        break
                    }
                    try {
                        lifecycleChanged.awaitNanos(remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                ownsClose = !timedOut && state != State.CLOSED
                if (ownsClose) closeStarted.set(true)
            }
        } finally {
            lifecycleLock.unlock()
        }
        if (!ownsClose) {
            if (interrupted) Thread.currentThread().interrupt()
            if (timedOut) throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT)
            return
        }
        if (interrupted) Thread.interrupted()
        val cleanupCall = runBounded(deadline) { closeOnce(deadline) }
        if (cleanupCall.completed) publishClosed()
        else cleanupCall.onCompletion { publishClosed() }
        lifecycleLock.lock()
        try {
            if (Thread.interrupted()) interrupted = true
        } finally {
            lifecycleLock.unlock()
        }
        if (interrupted) Thread.currentThread().interrupt()
        cleanupCall.failure?.let { throw it }
    }

    /** Must attempt every independent resource, aggregate failures, and be terminal and deadline-aware. */
    protected abstract fun closeOnce(deadline: GraphNativeBulkLoadDeadline)

    private fun publishClosed() {
        lifecycleLock.lock()
        try {
            state = State.CLOSED
            lifecycleChanged.signalAll()
        } finally {
            lifecycleLock.unlock()
        }
    }
}

class GraphNativeBulkLoadExecution<V : Any>(
    val operationName: String,
    val timeout: Duration,
    val maxFailureDetails: Int,
    val progressInterval: Long,
    val source: GraphNativeBulkLoadValidatedSource<V>,
    val cancellation: GraphNativeBulkLoadCancellationToken,
) {
    val timeoutNanos: Long
        get() = cancellation.timeoutNanos

    fun remainingNanos(): Long = cancellation.remainingNanos()

    override fun toString(): String =
        "GraphNativeBulkLoadExecution(timeout=$timeout, " +
            "maxFailureDetails=$maxFailureDetails, progressInterval=$progressInterval)"
}

fun interface GraphNativeBulkLoadSourceValidator<R : Any, V : Any> {
    /** Native command 전에 source kind, trust policy와 실행 위치를 검증한다. */
    fun validate(
        request: GraphNativeBulkLoadRequest<R>,
        capabilities: GraphNativeBulkLoaderCapabilities,
        cancellation: GraphNativeBulkLoadCancellationToken,
        validation: GraphNativeBulkLoadValidationContext,
    ): GraphNativeBulkLoadValidatedSource<V>
}

/** Validator가 반환 전에 만든 provisional resource의 rollback 소유권을 명시한다. */
class GraphNativeBulkLoadValidationContext {
    private val rollbackLock = ReentrantLock()
    private val rollbackActions = ArrayDeque<AutoCloseable>()
    private val pendingRollbackCalls = mutableSetOf<GraphNativeBulkLoadBoundedCall>()
    private var committed = false

    fun registerRollback(action: AutoCloseable) {
        rollbackLock.lock()
        try {
            check(!committed) { "validation rollback context is already committed" }
            rollbackActions.addFirst(action)
        } finally {
            rollbackLock.unlock()
        }
    }

    internal fun commit() {
        rollbackLock.lock()
        try {
            check(!committed) { "validation rollback context is already committed" }
            committed = true
            rollbackActions.clear()
        } finally {
            rollbackLock.unlock()
        }
    }

    internal fun rollback(deadline: GraphNativeBulkLoadDeadline): GraphNativeBulkLoadException? {
        val actions = rollbackLock.runLocked {
            if (committed) return null
            committed = true
            val snapshot = rollbackActions.toList()
            rollbackActions.clear()
            snapshot
        }
        if (actions.isEmpty()) return null
        val lateFailure = AtomicReference<GraphNativeBulkLoadException?>(null)
        val rollbackCall = runBounded(deadline) {
            actions.forEach { action ->
                try {
                    action.close()
                } catch (caught: GraphNativeBulkLoadException) {
                    lateFailure.updateAndGet { current ->
                        mergeRedactedFailure(current, redactNativeBulkLoadFailure(caught))
                    }
                } catch (_: Exception) {
                    lateFailure.updateAndGet { current ->
                        mergeRedactedFailure(
                            current,
                            GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN),
                        )
                    }
                }
            }
        }
        if (!rollbackCall.completed) {
            rollbackLock.runLocked { pendingRollbackCalls.add(rollbackCall) }
            rollbackCall.onCompletion {
                rollbackLock.runLocked { pendingRollbackCalls.remove(rollbackCall) }
            }
        }
        var failure = lateFailure.get()
        rollbackCall.failure?.let { failure = mergeRedactedFailure(failure, it) }
        return failure
    }
}

private inline fun <T> ReentrantLock.runLocked(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

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
                    require(succeeded == processed && failed == 0 && failures.isEmpty() && omittedFailureDetails == 0) {
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
                contractRequire(succeeded == 0 && failed == 0) {
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

fun interface GraphNativeBulkLoadProgressListener {
    fun onProgress(progress: GraphNativeBulkLoadProgress)
}

private class GraphNativeBulkLoadListenerFailure(
    val original: Throwable,
    val cancellationFailure: GraphNativeBulkLoadException?,
) : RuntimeException()

enum class GraphNativeBulkLoadDiagnosticKind {
    STARTED, COMPLETED, FAILED, CANCELLED, CLOSED
}

data class GraphNativeBulkLoadDiagnostic(
    val diagnosticId: String,
    val kind: GraphNativeBulkLoadDiagnosticKind,
    val backend: String,
    val operationName: String,
    val phase: GraphNativeBulkLoadPhase?,
    val elapsed: Duration,
    val outcome: GraphNativeBulkLoadOutcome?,
    val code: GraphNativeBulkLoadFailureCode?,
    val cancellationReason: GraphNativeBulkLoadCancellationReason? = null,
) : Serializable {
    init {
        diagnosticId.requireLogSafe("diagnosticId", 64)
        backend.requireLogSafe("backend", GraphNativeBulkLoaderCapabilities.MAX_BACKEND_LENGTH)
        operationName.requireLogSafe(
            "operationName",
            GraphNativeBulkLoadRequest.MAX_OPERATION_NAME_LENGTH,
        )
        require(operationName == GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME) {
            "operationName must be the fixed native-bulk-load operation label"
        }
        require(elapsed >= Duration.ZERO) { "elapsed must be >= 0" }
        require(kind != GraphNativeBulkLoadDiagnosticKind.CANCELLED || cancellationReason != null) {
            "CANCELLED diagnostic requires a cancellation reason"
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

fun interface GraphNativeBulkLoadDiagnosticObserver {
    /** Observer failures are swallowed after bounded redaction; they never alter load outcome. */
    fun onDiagnostic(diagnostic: GraphNativeBulkLoadDiagnostic)
}

private class GraphNativeBulkLoadProgressVerifier(
    private val progressInterval: Long,
    private val callerThread: Thread,
    private val listener: GraphNativeBulkLoadProgressListener?,
    private val cancel: () -> GraphNativeBulkLoadException?,
) {
    private var previous: GraphNativeBulkLoadProgress? = null
    private var previousPhase = -1
    private var callbackCount = 0L
    private var completeSeen = false

    fun onProgress(progress: GraphNativeBulkLoadProgress) {
        contractRequire(Thread.currentThread() === callerThread) {
            "progress callbacks must run on the load caller thread"
        }
        contractRequire(!completeSeen) { "progress cannot be emitted after COMPLETE" }
        contractRequire(progress.phase.ordinal >= previousPhase) { "progress phase regressed" }
        previous?.let {
            contractRequire(progress.processed >= it.processed) { "processed count regressed" }
            contractRequire(progress.succeeded >= it.succeeded) { "succeeded count regressed" }
            contractRequire(progress.failed >= it.failed) { "failed count regressed" }
        }
        if (progress.eventKind == GraphNativeBulkLoadProgressEventKind.PHASE) {
            contractRequire(
                if (previous == null) progress.phase == GraphNativeBulkLoadPhase.PREPARE
                else progress.phase.ordinal > previousPhase,
            ) {
                "phase progress must represent a phase transition"
            }
        } else {
            contractRequire(previous != null) { "interval progress requires an initial PHASE event" }
            contractRequire(progress.phase.ordinal == previousPhase) {
                "interval progress must remain within the current phase"
            }
            contractRequire(progress.phase != GraphNativeBulkLoadPhase.COMPLETE) {
                "interval progress cannot be emitted at COMPLETE"
            }
            val previousProgress = requireNotNull(previous)
            contractRequire(progress.processed > previousProgress.processed) {
                "interval progress must advance processed count"
            }
            val previousBucket = previousProgress.processed / progressInterval
            val currentBucket = progress.processed / progressInterval
            contractRequire(currentBucket > previousBucket) {
                "interval progress must cross a progress token boundary"
            }
        }
        val buckets = if (progress.processed == 0L) 0L
        else 1L + (progress.processed - 1L) / progressInterval
        val intervalBudget = if (buckets > Long.MAX_VALUE - 5L) {
            Long.MAX_VALUE
        } else {
            5L + buckets
        }
        val maxCallbacks = minOf(GraphNativeBulkLoadRequest.MAX_PROGRESS_CALLBACKS, intervalBudget)
        contractRequire(callbackCount < maxCallbacks) { "progress callback budget exceeded" }
        callbackCount++
        previousPhase = progress.phase.ordinal
        previous = progress
        if (progress.phase == GraphNativeBulkLoadPhase.COMPLETE) {
            completeSeen = true
        }
        try {
            listener?.onProgress(progress)
        } catch (failure: Throwable) {
            throw GraphNativeBulkLoadListenerFailure(failure, cancel())
        }
    }

    fun requireTerminal(report: GraphNativeBulkLoadReport) {
        contractRequire(completeSeen) { "load must emit exactly one COMPLETE progress event" }
        val terminal = requireNotNull(previous) { "missing terminal progress" }
        contractRequire(terminal.outcome == report.outcome) {
            "terminal progress outcome does not match report"
        }
        contractRequire(terminal.processed == report.processed &&
            terminal.succeeded == report.succeeded &&
            terminal.failed == report.failed
        ) {
            "terminal progress counts do not match report"
        }
    }

    fun lastPhase(): GraphNativeBulkLoadPhase? = previous?.phase
}

private val graphNativeBulkLoadDiagnosticSequence = AtomicLong()

abstract class GraphNativeBulkLoader<R : Any, V : Any>(
    final val capabilities: GraphNativeBulkLoaderCapabilities,
    final val sourceValidator: GraphNativeBulkLoadSourceValidator<R, V>,
    private val diagnosticObserver: GraphNativeBulkLoadDiagnosticObserver? = null,
) : AutoCloseable {
    private enum class State { OPEN, LOADING, CLOSING, CLOSED }

    private val lifecycleLock = ReentrantLock()
    private val lifecycleChanged: Condition = lifecycleLock.newCondition()
    private val diagnosticInFlight = AtomicBoolean(false)
    private val diagnosticDisabled = AtomicBoolean(false)
    private val pendingDiagnostic = AtomicReference<GraphNativeBulkLoadDiagnostic?>(null)
    /** Allows one bounded retry after the observer circuit opens. */
    private val timeoutDiagnosticAttempted = AtomicBoolean(false)
    private var state = State.OPEN
    private var loadInFlight = false
    private var loadingThread: Thread? = null
    private val closeStarted = AtomicBoolean(false)
    private var closingThread: Thread? = null
    private var activeCancellation: GraphNativeBulkLoadCancellationToken? = null
    private var activeDiagnosticId: String? = null

    final fun load(
        request: GraphNativeBulkLoadRequest<R>,
        listener: GraphNativeBulkLoadProgressListener? = null,
    ): GraphNativeBulkLoadReport {
        val startedNanos = System.nanoTime()
        val diagnosticId = newDiagnosticId()
        requireSupportedRequest(request)
        val cancellation = beginLoad(request.timeout, diagnosticId)
        emitDiagnostic(
            kind = GraphNativeBulkLoadDiagnosticKind.STARTED,
            startedNanos = startedNanos,
            phase = GraphNativeBulkLoadPhase.PREPARE,
            operationName = request.operationName,
            deadline = cancellation.deadline(),
            diagnosticId = diagnosticId,
        )
        val verifier = GraphNativeBulkLoadProgressVerifier(
            progressInterval = request.progressInterval,
            callerThread = Thread.currentThread(),
            listener = listener,
            cancel = {
                cancellation.request(
                    GraphNativeBulkLoadCancellationReason.LISTENER_FAILURE,
                    cancellation.deadline(),
                )
                cancellation.cancellationHookFailure()
            },
        )
        var validated: GraphNativeBulkLoadValidatedSource<V>? = null
        var report: GraphNativeBulkLoadReport? = null
        var primaryFailure: Throwable? = null
        var deferredCleanupOwner = false
        try {
            val validation = GraphNativeBulkLoadValidationContext()
            validated = try {
                val handle = sourceValidator.validate(request, capabilities, cancellation, validation)
                validation.commit()
                handle
            } catch (failure: GraphNativeBulkLoadException) {
                val redacted = redactNativeBulkLoadFailure(failure)
                validation.rollback(cancellation.deadline())?.let { redacted.addSuppressed(it) }
                throw redacted
            } catch (_: Exception) {
                val rejected = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.SOURCE_REJECTED)
                validation.rollback(cancellation.deadline())?.let { rejected.addSuppressed(it) }
                throw rejected
            } catch (failure: Throwable) {
                validation.rollback(cancellation.deadline())
                throw failure
            }
            cancellation.check()
            report = try {
                loadValidated(
                    GraphNativeBulkLoadExecution(
                        operationName = request.operationName,
                        timeout = request.timeout ?: GraphNativeBulkLoadRequest.DEFAULT_TIMEOUT,
                        maxFailureDetails = request.maxFailureDetails,
                        progressInterval = request.progressInterval,
                        source = validated,
                        cancellation = cancellation,
                    ),
                    verifier::onProgress,
                )
            } catch (failure: GraphNativeBulkLoadListenerFailure) {
                throw failure
            } catch (failure: GraphNativeBulkLoadException) {
                throw redactNativeBulkLoadFailure(failure)
            } catch (_: IllegalArgumentException) {
                throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION)
            } catch (_: Exception) {
                throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.NATIVE_COMMAND_FAILED)
            }
            cancellation.check()
            verifier.requireTerminal(report)
            report.requireCompatible(request, capabilities)
        } catch (failure: GraphNativeBulkLoadListenerFailure) {
            val listenerFailure = failure.original
            failure.cancellationFailure?.let { listenerFailure.addSuppressed(it) }
            primaryFailure = listenerFailure
        } catch (failure: GraphNativeBulkLoadException) {
            primaryFailure = redactNativeBulkLoadFailure(failure)
        } catch (_: IllegalArgumentException) {
            primaryFailure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION)
        } catch (_: Exception) {
            primaryFailure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
        } finally {
            validated?.let { source ->
                try {
                    source.close()
                } catch (failure: GraphNativeBulkLoadException) {
                    primaryFailure = mergeFailure(primaryFailure, redactNativeBulkLoadFailure(failure))
                } catch (_: Exception) {
                    primaryFailure = mergeFailure(
                        primaryFailure,
                        GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN),
                    )
                }
            }
            deferredCleanupOwner = finishLoad()
            val terminal = report
            fun emitLoadTerminal(
                finalFailure: Throwable?,
                diagnosticDeadline: GraphNativeBulkLoadDeadline,
            ) {
                val failureCode = (finalFailure as? GraphNativeBulkLoadException)?.code
                    ?: if (finalFailure != null) GraphNativeBulkLoadFailureCode.UNKNOWN else null
                val exceptionCancellation = (finalFailure as? GraphNativeBulkLoadCancellationException)?.reason
                val reportCancellation = terminal?.cancellationReason ?: exceptionCancellation ?: cancellation.reason
                val terminalCancellation = reportCancellation ?: when (failureCode) {
                    GraphNativeBulkLoadFailureCode.TIMEOUT -> GraphNativeBulkLoadCancellationReason.TIMEOUT
                    GraphNativeBulkLoadFailureCode.CANCELLED -> GraphNativeBulkLoadCancellationReason.CLOSE
                    else -> GraphNativeBulkLoadCancellationReason.LISTENER_FAILURE
                }
                val isCancelled = terminal?.outcome == GraphNativeBulkLoadOutcome.CANCELLED ||
                    reportCancellation != null ||
                    failureCode == GraphNativeBulkLoadFailureCode.CANCELLED ||
                    failureCode == GraphNativeBulkLoadFailureCode.TIMEOUT
                emitDiagnostic(
                    kind = when {
                        finalFailure == null && terminal?.outcome == GraphNativeBulkLoadOutcome.COMPLETED ->
                            GraphNativeBulkLoadDiagnosticKind.COMPLETED
                        isCancelled ->
                            GraphNativeBulkLoadDiagnosticKind.CANCELLED
                        else -> GraphNativeBulkLoadDiagnosticKind.FAILED
                    },
                    startedNanos = startedNanos,
                    phase = verifier.lastPhase(),
                    outcome = terminal?.outcome,
                    code = failureCode,
                    cancellationReason = if (isCancelled) terminalCancellation else null,
                    operationName = request.operationName,
                    deadline = diagnosticDeadline,
                    diagnosticId = diagnosticId,
                )
            }
            if (deferredCleanupOwner) {
                val loadFailureBeforeCleanup = primaryFailure
                val deferredDeadline = GraphNativeBulkLoadDeadline(
                    saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
                )
                val diagnosticDeadline = earliestDeadline(cancellation.deadline(), deferredDeadline)
                val deferredFailure = closeResourcesTerminal(
                    initialFailure = null,
                    startedNanos = startedNanos,
                    operationName = request.operationName,
                    diagnosticId = diagnosticId,
                    deadline = deferredDeadline,
                    emitClosed = false,
                    onClosed = { cleanupFailure ->
                        val finalFailure = cleanupFailure?.let {
                            mergeFailure(loadFailureBeforeCleanup, it)
                        } ?: loadFailureBeforeCleanup
                        emitLoadTerminal(finalFailure, diagnosticDeadline)
                        emitDiagnostic(
                            kind = GraphNativeBulkLoadDiagnosticKind.CLOSED,
                            startedNanos = startedNanos,
                            phase = GraphNativeBulkLoadPhase.COMPLETE,
                            code = (finalFailure as? GraphNativeBulkLoadException)?.code
                                ?: if (finalFailure != null) GraphNativeBulkLoadFailureCode.UNKNOWN else null,
                            operationName = request.operationName,
                            deadline = diagnosticDeadline,
                            diagnosticId = diagnosticId,
                        )
                    },
                )
                deferredFailure?.let { primaryFailure = mergeFailure(primaryFailure, it) }
            } else {
                emitLoadTerminal(primaryFailure, cancellation.deadline())
            }
        }
        primaryFailure?.let { throw it }
        return report ?: throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
    }

    private fun beginLoad(timeout: Duration?, diagnosticId: String): GraphNativeBulkLoadCancellationToken {
        val effectiveTimeout = timeout ?: GraphNativeBulkLoadRequest.DEFAULT_TIMEOUT
        val cancellation = GraphNativeBulkLoadCancellationToken(
            startedNanos = System.nanoTime(),
            timeoutNanos = effectiveTimeout.toNanos(),
            cancellationHook = ::requestCancellationSafely,
        )
        lifecycleLock.lock()
        try {
            check(state == State.OPEN) { "native bulk loader is not open" }
            state = State.LOADING
            loadInFlight = true
            loadingThread = Thread.currentThread()
            activeCancellation = cancellation
            activeDiagnosticId = diagnosticId
        } finally {
            lifecycleLock.unlock()
        }
        return cancellation
    }

    private fun requireSupportedRequest(request: GraphNativeBulkLoadRequest<R>) {
        if (!capabilities.supported || request.sourceKind !in capabilities.sourceKinds) {
            throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
        }
    }

    /** Returns true when the load thread inherits terminal cleanup ownership after close grace expiry. */
    private fun finishLoad(): Boolean {
        lifecycleLock.lock()
        try {
            loadInFlight = false
            loadingThread = null
            activeCancellation = null
            val deferredOwner = state == State.CLOSING && closeStarted.compareAndSet(false, true)
            if (deferredOwner) closingThread = Thread.currentThread()
            if (state == State.LOADING) {
                state = State.OPEN
                activeDiagnosticId = null
            }
            lifecycleChanged.signalAll()
            return deferredOwner
        } finally {
            lifecycleLock.unlock()
        }
    }

    override fun close() {
        val closeStartedNanos = System.nanoTime()
        var diagnosticId = newDiagnosticId()
        var interrupted = Thread.interrupted()
        var closeFailure: GraphNativeBulkLoadException? = null
        var cancellation: GraphNativeBulkLoadCancellationToken? = null
        lifecycleLock.lock()
        try {
            val currentThread = Thread.currentThread()
            if ((state == State.LOADING && loadingThread === currentThread) ||
                (state == State.CLOSING && (loadingThread === currentThread || closingThread === currentThread))
            ) {
                if (interrupted) currentThread.interrupt()
                throw IllegalStateException("close() cannot be re-entered from an active lifecycle callback")
            }
            when (state) {
                State.OPEN -> {
                    state = State.CLOSING
                    activeDiagnosticId = diagnosticId
                }
                State.LOADING -> {
                    state = State.CLOSING
                    cancellation = activeCancellation
                    diagnosticId = activeDiagnosticId ?: diagnosticId
                }
                State.CLOSING -> {
                    cancellation = activeCancellation
                    diagnosticId = activeDiagnosticId ?: diagnosticId
                }
                State.CLOSED -> Unit
            }
        } finally {
            lifecycleLock.unlock()
        }
        if (stateSnapshot() == State.CLOSED) {
            if (interrupted) Thread.currentThread().interrupt()
            return
        }

        val closeDeadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(closeStartedNanos, GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        )
        cancellation?.request(GraphNativeBulkLoadCancellationReason.CLOSE, closeDeadline)
        cancellation?.cancellationHookFailure()?.let { closeFailure = it }
        var ownsClose = false
        var closeTimedOut = false
        lifecycleLock.lock()
        try {
            while (loadInFlight) {
                val remaining = closeDeadline.remainingNanos()
                if (remaining <= 0L) {
                    closeTimedOut = true
                    break
                }
                try {
                    lifecycleChanged.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    interrupted = true
                    cancellation?.request(GraphNativeBulkLoadCancellationReason.INTERRUPT, closeDeadline)
                }
            }
            if (!closeTimedOut && state != State.CLOSED) {
                ownsClose = closeStarted.compareAndSet(false, true)
                if (ownsClose) closingThread = Thread.currentThread()
                while (!ownsClose && state != State.CLOSED) {
                    val remaining = closeDeadline.remainingNanos()
                    if (remaining <= 0L) {
                        closeTimedOut = true
                        break
                    }
                    try {
                        lifecycleChanged.awaitNanos(remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
        } finally {
            lifecycleLock.unlock()
        }
        cancellation?.cancellationHookFailure()?.let { closeFailure = mergeFailure(closeFailure, it) }
        if (closeTimedOut) closeFailure = mergeFailure(
            closeFailure,
            GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT),
        )
        if (closeTimedOut) {
            emitDiagnostic(
                kind = GraphNativeBulkLoadDiagnosticKind.CANCELLED,
                startedNanos = closeStartedNanos,
                phase = GraphNativeBulkLoadPhase.COMPLETE,
                code = GraphNativeBulkLoadFailureCode.TIMEOUT,
                cancellationReason = cancellation?.reason ?: GraphNativeBulkLoadCancellationReason.CLOSE,
                deadline = closeDeadline,
                diagnosticId = diagnosticId,
            )
        }
        if (ownsClose) {
            closeFailure = closeResourcesTerminal(
                initialFailure = closeFailure,
                interruptedAtEntry = interrupted,
                startedNanos = closeStartedNanos,
                diagnosticId = diagnosticId,
                deadline = closeDeadline,
            )
        }
        if (interrupted) Thread.currentThread().interrupt()
        closeFailure?.let { throw it }
    }

    private fun closeResourcesTerminal(
        initialFailure: GraphNativeBulkLoadException?,
        interruptedAtEntry: Boolean = Thread.interrupted(),
        startedNanos: Long = System.nanoTime(),
        operationName: String = "native-bulk-load",
        diagnosticId: String = newDiagnosticId(),
        deadline: GraphNativeBulkLoadDeadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        ),
        emitClosed: Boolean = true,
        onClosed: (GraphNativeBulkLoadException?) -> Unit = {},
    ): GraphNativeBulkLoadException? {
        var interrupted = interruptedAtEntry
        var failure = initialFailure
        if (interrupted) Thread.interrupted()
        val cleanupCall = runBounded(deadline) { closeResources(deadline) }

        fun publishClosed(finalFailure: GraphNativeBulkLoadException?) {
            lifecycleLock.lock()
            try {
                state = State.CLOSED
                closingThread = null
                activeDiagnosticId = null
                lifecycleChanged.signalAll()
            } finally {
                lifecycleLock.unlock()
            }
            if (emitClosed) {
                emitDiagnostic(
                    GraphNativeBulkLoadDiagnosticKind.CLOSED,
                    startedNanos,
                    GraphNativeBulkLoadPhase.COMPLETE,
                    code = finalFailure?.code,
                    operationName = operationName,
                    deadline = deadline,
                    diagnosticId = diagnosticId,
                )
            }
            onClosed(finalFailure)
        }

        if (cleanupCall.completed) {
            cleanupCall.failure?.let { failure = mergeFailure(failure, it) }
            publishClosed(failure)
        } else {
            cleanupCall.failure?.let { failure = mergeFailure(failure, it) }
            val initialForCallback = failure
            cleanupCall.onCompletion { lateFailure ->
                val finalFailure = lateFailure?.let { mergeFailure(initialForCallback, it) }
                    ?: initialForCallback
                publishClosed(finalFailure)
            }
        }
        if (Thread.interrupted()) interrupted = true
        if (interrupted) Thread.currentThread().interrupt()
        return failure
    }

    private fun stateSnapshot(): State {
        lifecycleLock.lock()
        return try { state } finally { lifecycleLock.unlock() }
    }

    private fun requestCancellationSafely(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline,
    ): GraphNativeBulkLoadException? {
        val interrupted = Thread.interrupted()
        var wasInterrupted = interrupted
        return try {
            runBounded(deadline) { requestCancellation(reason, deadline) }.failure
        } finally {
            if (Thread.interrupted()) wasInterrupted = true
            if (wasInterrupted) Thread.currentThread().interrupt()
        }
    }

    private fun emitDiagnostic(
        kind: GraphNativeBulkLoadDiagnosticKind,
        startedNanos: Long,
        phase: GraphNativeBulkLoadPhase?,
        outcome: GraphNativeBulkLoadOutcome? = null,
        code: GraphNativeBulkLoadFailureCode? = null,
        cancellationReason: GraphNativeBulkLoadCancellationReason? = null,
        operationName: String = "native-bulk-load",
        deadline: GraphNativeBulkLoadDeadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        ),
        diagnosticId: String,
    ) {
        val observer = diagnosticObserver ?: return
        val elapsedNanos = (System.nanoTime() - startedNanos).coerceAtLeast(0L)
        val diagnostic = GraphNativeBulkLoadDiagnostic(
            diagnosticId = diagnosticId,
            kind = kind,
            backend = capabilities.backend,
            operationName = operationName,
            phase = phase,
            elapsed = Duration.ofNanos(elapsedNanos),
            outcome = outcome,
            code = code,
            cancellationReason = cancellationReason,
        )
        val timeoutDiagnostic = diagnostic.kind == GraphNativeBulkLoadDiagnosticKind.CANCELLED &&
            diagnostic.code == GraphNativeBulkLoadFailureCode.TIMEOUT
        if (diagnosticDisabled.get()) {
            if (timeoutDiagnostic) {
                pendingDiagnostic.set(diagnostic)
                dispatchPendingDiagnostic(observer)
            }
            return
        }
        if (!diagnosticInFlight.compareAndSet(false, true)) {
            if (timeoutDiagnostic) pendingDiagnostic.set(diagnostic)
            return
        }
        dispatchDiagnostic(observer, diagnostic, deadline, retryAttempt = false)
    }

    private fun dispatchDiagnostic(
        observer: GraphNativeBulkLoadDiagnosticObserver,
        diagnostic: GraphNativeBulkLoadDiagnostic,
        deadline: GraphNativeBulkLoadDeadline,
        retryAttempt: Boolean,
    ) {
        if (deadline.isExpired) {
            Thread.startVirtualThread {
                try {
                    val dispatchCall = runBounded(
                        GraphNativeBulkLoadDeadline(
                            saturatingAdd(
                                System.nanoTime(),
                                GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos(),
                            ),
                        ),
                    ) {
                        observer.onDiagnostic(diagnostic)
                    }
                    if (!dispatchCall.completed) {
                        disableDiagnostics(retryAttempt)
                        dispatchCall.onCompletion {
                            diagnosticInFlight.set(false)
                            dispatchPendingDiagnostic(observer)
                        }
                    } else {
                        if (dispatchCall.failure?.code == GraphNativeBulkLoadFailureCode.TIMEOUT) {
                            disableDiagnostics(retryAttempt)
                        } else if (dispatchCall.failure == null) {
                            diagnosticDisabled.set(false)
                            timeoutDiagnosticAttempted.set(false)
                        }
                        diagnosticInFlight.set(false)
                        dispatchPendingDiagnostic(observer)
                    }
                } catch (_: Exception) {
                    diagnosticInFlight.set(false)
                    dispatchPendingDiagnostic(observer)
                }
            }
            return
        }
        try {
            val observerCall = runBounded(deadline) {
                observer.onDiagnostic(diagnostic)
            }
            if (!observerCall.completed) {
                disableDiagnostics(retryAttempt)
                observerCall.onCompletion {
                    diagnosticInFlight.set(false)
                    dispatchPendingDiagnostic(observer)
                }
            } else {
                if (observerCall.failure?.code == GraphNativeBulkLoadFailureCode.TIMEOUT) {
                    disableDiagnostics(retryAttempt)
                } else if (observerCall.failure == null) {
                    diagnosticDisabled.set(false)
                    timeoutDiagnosticAttempted.set(false)
                }
                diagnosticInFlight.set(false)
                dispatchPendingDiagnostic(observer)
            }
        } catch (_: Exception) {
            diagnosticInFlight.set(false)
            dispatchPendingDiagnostic(observer)
            // Observer setup failures are intentionally not part of the public outcome.
        }
    }

    private fun dispatchPendingDiagnostic(observer: GraphNativeBulkLoadDiagnosticObserver) {
        val pending = pendingDiagnostic.getAndSet(null) ?: return
        if (!diagnosticInFlight.compareAndSet(false, true)) {
            pendingDiagnostic.set(pending)
            return
        }
        val timeoutDiagnostic = pending.kind == GraphNativeBulkLoadDiagnosticKind.CANCELLED &&
            pending.code == GraphNativeBulkLoadFailureCode.TIMEOUT
        val retryAttempt = diagnosticDisabled.get() && timeoutDiagnostic &&
            timeoutDiagnosticAttempted.compareAndSet(false, true)
        if (diagnosticDisabled.get() && !retryAttempt) {
            diagnosticInFlight.set(false)
            pendingDiagnostic.set(pending)
            return
        }
        diagnosticDisabled.set(false)
        dispatchDiagnostic(
            observer,
            pending,
            // A pending event is already past its parent deadline; keep retry dispatch asynchronous
            // so a close/load caller never inherits a fresh observer grace period.
            GraphNativeBulkLoadDeadline(System.nanoTime()),
            retryAttempt = retryAttempt,
        )
    }

    private fun disableDiagnostics(retryAttempt: Boolean) {
        diagnosticDisabled.set(true)
        timeoutDiagnosticAttempted.set(retryAttempt)
    }

    private fun newDiagnosticId(): String =
        "diag-${graphNativeBulkLoadDiagnosticSequence.incrementAndGet().toString(36)}"

    protected open fun requestCancellation(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline,
    ) {}

    /** Must attempt every independent resource, aggregate failures, and be terminal and deadline-aware. */
    protected open fun closeResources(deadline: GraphNativeBulkLoadDeadline) {}

    protected abstract fun loadValidated(
        execution: GraphNativeBulkLoadExecution<V>,
        listener: GraphNativeBulkLoadProgressListener?,
    ): GraphNativeBulkLoadReport

    private fun mergeFailure(
        primary: GraphNativeBulkLoadException?,
        additional: GraphNativeBulkLoadException,
    ): GraphNativeBulkLoadException {
        if (primary == null) return additional
        primary.addSuppressed(additional)
        return primary
    }

    private fun mergeFailure(
        primary: Throwable?,
        additional: GraphNativeBulkLoadException,
    ): Throwable {
        if (primary == null) return additional
        primary.addSuppressed(additional)
        return primary
    }
}

class UnsupportedGraphNativeBulkLoader<R : Any, V : Any>(
    backend: String,
) : GraphNativeBulkLoader<R, V>(
    capabilities = GraphNativeBulkLoaderCapabilities(
        backend = backend,
        supported = false,
        sourceKinds = emptySet(),
        transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.UNKNOWN,
        failureDetail = GraphNativeBulkLoadFailureDetail.NONE,
    ),
    sourceValidator = GraphNativeBulkLoadSourceValidator { _, _, _, _ ->
        throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
    },
) {
    override fun loadValidated(
        execution: GraphNativeBulkLoadExecution<V>,
        listener: GraphNativeBulkLoadProgressListener?,
    ): GraphNativeBulkLoadReport =
        throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
}
```

실제 선언은 `java.time.Duration`,
`java.util.concurrent.atomic.AtomicBoolean`/`AtomicLong`/`AtomicReference`,
`java.util.concurrent.locks.ReentrantLock`/`Condition`과
프로젝트의 `requireNotBlank` 관례를 따른다.
`operationName`은 타입/값으로 고정된 `native-bulk-load` operation label이며 공백,
CR/LF와 제어문자를 허용하지 않는다. tenant ID, user input, credential,
request correlation ID를 절대 넣을 수 없다. `timeout`을 지정하면 adapter가 반드시
준수하고, 생략할 때도 base가 유한한 기본 timeout과 monotonic
start/elapsed deadline을 execution에 넣는다. 지원 timeout은 최대 365일로
제한해 nano 단위 overflow를 거절한다. adapter는 `execution.remainingNanos()`
이하로 native timeout을 설정하고 call 직전 `execution.cancellation.check()`를
호출해야 한다. `maxFailureDetails`는 0~1,024 범위에서 report가
보관할 상세 개수의 상한이며, 공개 message는 failure code의 고정 문구다.
`omittedFailureDetails`로 잘린 개수를 표시한다. `progressInterval`은 처리된
record 수 기준 callback 간격이다. base progress verifier는 phase 순서, 누적
count, caller thread, COMPLETE 횟수, event kind와 load당 절대 1,024회 callback
상한을 모든 listener 유무에서
검증한다. adapter는 phase 전환 경계와
`progressInterval` token 경계에서만 event를 보내며, 임의의 batch event를
추가하지 않는다. record 수를 알 수 없는 adapter는 phase 경계 event만 보낼 수
있고, 이 경우에도 고정 phase 상한을 넘지 않는다.
취소 trigger는 nullable sentinel이 아니라 `TIMEOUT`, `INTERRUPT`, `CLOSE`,
`LISTENER_FAILURE` 중 하나이며 token의 첫 trigger가 bounded
`requestCancellation` hook을 정확히 한 번 호출한다. timeout/interrupt가
`check()`에서 발견되는 경우에도 같은 원자적 경로를 사용한다. progress/report
postcondition 위반은 caller `INVALID_REQUEST`가 아닌 fixed
`CONTRACT_VIOLATION`으로 매핑한다.
request의 generic `R` source는 항상 caller 소유의 opaque reference이고,
validated generic `V`와 타입 수준에서 분리된다. adapter는
원본 source를 닫거나 보관하지 않으며, validator가 반환한 typed validated
handle만 adapter 소유로 닫는다.

`supported`는 native lane 자체의 지원 여부를 나타내며, 지원되는 경우에만
`sourceKinds`를 채운다. adapter는 `sourceValidator.validate(request,
capabilities, cancellation, validationContext)`를 native command 전에 반드시 호출하고
cancellation token을 source I/O 직전에 확인한다. 이 SPI는 source를
열거나 복사하거나 URI를 dereference하지 않는다.
validator가 반환 전에 만든 임시 session/staging 자원은
`GraphNativeBulkLoadValidationContext.registerRollback()`에 등록한다. validation이
실패·취소하면 base가 context rollback을 역순으로 실행하고, 성공 시 `commit()`으로
반환된 validated handle만 cleanup 소유권으로 승격한다. rollback은 하나의
deadline-bound owner call로 실행하고 late completion을 추적하며, deadline 이후
새 cleanup worker를 만들지 않는다. validator가 context 밖에
만든 provisional resource는 지원 계약 위반이며 supported adapter가 될 수 없다.

`GraphNativeBulkLoadDiagnosticObserver`는 선택적 hook이며, base는 secret-free
diagnostic fields만 `STARTED`/terminal/`CLOSED` 시점에 발행한다. observer 구현체는
`KLogging`으로 backend, operation label, phase, elapsed, outcome, code,
diagnosticId를 구조화할 수 있지만 raw Throwable/cause/suppressed/source는 기록하지
않는다. observer 실패는 redacted mapping 후 폐기하고 load/close primary를 바꾸지
않는다.

validator가 반환한 `GraphNativeBulkLoadValidatedSource<V>`가 native load의 유일한
source 입력이다. validation 이후 adapter는 `request.source`를 다시 읽거나
문자열화하거나 command·log·staging에 사용하지 않으며, 성공·실패·취소·listener
예외 모든 경로에서 handle을 정확히 한 번 닫는다. 이 core 이슈의 validator
테스트는 fake handle로 이 순서와 단일 close를 검증한다.

`GraphNativeBulkLoadSourcePolicy`는 URI 접근을 기본 `DENIED`로 두고,
exact origin (scheme, canonicalHost, port) allowlist, private-network,
redirect-hop, URI-length 정책과 승인된 staging root의 필요 여부를
capabilities에 고정한다. `allowCredentials`는 항상 `false`여야 하며
`ALLOWLISTED`는 하나 이상의 exact origin을 요구한다. URI를 허용하는 adapter는 caller가 제공한 값이 policy를 통과하는지
검증한 뒤에만 command를 실행한다. `execution`은 source를 `CALLER_JVM`에서
해석하는지 `BACKEND_SERVER`에서 해석하는지 명시한다. backend-server 경로는
임의 path가 아니라 adapter가 소유한 승인 staging artifact만 받으며, 실행
지점에서 같은 exact origin과 artifact binding을 다시 검증한다.
`BACKEND_SERVER` URI 경로는 `backendRevalidatesOrigin = true`, FILE/DIRECTORY
경로는 `backendRevalidatesArtifact = true`를 선언하지 않으면 허용하지 않는다.
URI는 정규화한 뒤 user-info/query/fragment를 기본 거절하고,
최대 redirect hop 안의 모든 resolved address·IP literal에 exact origin
allowlist를 재적용해 DNS rebinding을 차단한다. file/directory adapter는
canonical path와 승인된 staging root를 비교하고 symlink/traversal 및 TOCTOU
escape를 거절한다.

`backend`는 로그 안전한 bounded identifier로 검증하며, failure code는 enum으로
제한한다. identifier allowlist는 ASCII 영숫자로 시작하고 `[A-Za-z0-9._-]`만
허용하며 backend는 최대 64자다. operation label은 별도 입력값이 아니라
고정 문자열 `native-bulk-load`만 허용한다.

`GraphNativeBulkLoadFailure`는 stable enum `code`와 code의 고정 public message만
공개한다. `GraphNativeBulkLoadException`은 raw cause를 보유하지 않는 redacted
public boundary다. adapter-origin exception은 code/reason만 복사한 새 boundary로
재생성하며 cause와 suppressed를 전달하지 않는다. adapter가 만드는 report/progress/exception message에는 원시
`Throwable`, native command, URI user-info/query, credential, 서버 경로와 payload
fragment를 포함하지 않는다. 단, listener가 호출자 소유의 예외를 던진 경우에는
indeterminate 경계를 보존하기 위해 base loader가 동일한 예외 인스턴스를
호출자에게 다시 던진다.
report factory는 request와 capabilities를 함께 받아 operation label, detail limit,
`ATOMIC` durable-count, `PARTIAL` 조건과 `failureDetail = NONE`을 한 경계에서
검증한다. adapter가 임의 operationName/detail limit을 주입하거나 factory 이후
capability invariant를 우회할 수 없으며, base loader는 반환 직후 동일 검증을
다시 수행한다.
`UnsupportedGraphNativeBulkLoader<R, V>`는 `supported = false`, 빈 source kind,
`failureDetail = NONE`, URI `DENIED` policy와 항상 실패하는 validator를
노출하고 load 시 fixed `GraphNativeBulkLoadException`을 던진다.
operation label과 source는 예외 문자열에 넣지 않는다. 이 경로는 조용한
backend-neutral fallback을 만들지 않는다.

## 호출 흐름

1. backend 모듈이 자신의 source 타입과 loader를 구현하고 capabilities를
   선언한다.
2. 애플리케이션은 source kind와 operation name을 포함한 request를 만든다.
3. 호출자는 capabilities를 확인한 뒤 `load`를 호출하고 progress listener를
   선택적으로 연결한다.
4. base loader는 lifecycle 상태를 `LOADING`으로 선형화한 뒤 adapter는
   request/capability 검사 후 rollback context와 함께 `sourceValidator`를 호출하고, 검증된
   opaque source handle/artifact를 만든 뒤에만 최초 I/O, staging, logging,
   command 생성·실행을 시작한다. backend-server 실행은 서버 측에서 같은
   artifact와 주소 binding을 다시 검증한다.
5. adapter는 단계·배치 경계에서만 immutable progress를 동기적으로 보고하고,
   partial failure를 bounded native detail 수준으로 report에 보존한다.
6. 호출자는 report를 기록하고 `close()`로 driver/session/file staging 자원을
   해제한다.

## lifecycle·동시성·취소 계약

- loader 상태는 `OPEN → LOADING → OPEN` 또는 `OPEN/LOADING → CLOSING → CLOSED`다.
- 한 loader에는 동시에 하나의 `load`만 허용한다. 재진입·두 번째 호출은
  backend command 전에 `IllegalStateException`으로 실패한다.
- `close()`는 idempotent해야 하며, `CLOSED` 이후 `load`는
  `IllegalStateException`으로 실패한다. 구현체는 `close()`와 `load()`의 경쟁을
  `CLOSING` 상태로 선형화하고, 진행 중인 호출에 cancellation을 요청한 뒤
  native resource를 닫기 전에 고정된 close grace 안에서 호출이 끝날 때까지
  interrupt를 기록하면서 uninterruptibly 기다린 뒤 interrupt flag를 복원한다.
  grace가 만료되면 `CLOSING`을 유지하고 redacted timeout을 반환한다. cancellation
  hook과 terminal cleanup은 `GraphNativeBulkLoadDeadline`을 받아
  `Thread.startVirtualThread` 기반 bounded call로 실행하며, deadline을 넘긴 worker는
  interrupt하고 호출자는 TIMEOUT으로 즉시 복귀한다. 지원 adapter는 native driver가
  interrupt를 관찰해 worker를 종료할 수 있음을 검증해야 한다. 그 뒤
  load가 종료되는 순간 `finishLoad()`가 아직 owner가 없을 때만 terminal cleanup
  owner를 원자적으로 획득하고 `closeResources()`를 실행한다. 따라서 두 번째
  `close()` 없이도 loader는 `CLOSED`로 수렴하며, 이미 다른 close 호출이 owner인
  경우에는 그 호출이 cleanup을 완료한다. validated source의 `close()`가
  in-flight `takeOnce()` 때문에 grace timeout을 반환한 경우에도 `take()`의
  종료 경로가 close owner를 원자적으로 인계받아 `closeOnce()`를 실행하고
  `CLOSED`를 publish한다. 따라서 source 단독 사용에서도 두 번째 `close()`가
  없어도 resource가 누수되지 않는다. cleanup hook은 기존 interrupt를
  clear하고 새 interrupt를 기록하며, 모든 독립 자원을 시도·집계한 terminal
  invocation이 반환하거나 redacted failure를 던진 뒤에만 `CLOSED`를 publish한다.
  여러 `close()` 호출에도 validated `closeOnce()`와 loader `closeResources()`는
  한 번만 실행한다. `requestCancellation` hook은 load lock을 기다리거나 blocking native
  call을 직접 수행하지 않는 bounded signal이어야 하며, adapter가 native cancel 또는
  terminal cleanup을 bounded하게 완료할 수 없는 경우 `shutdownGuarantee = UNKNOWN`,
  `supported = false`인 loader로 선언한다. `BOUNDED` capability는 cancellation hook,
  validated `closeOnce()`와 `closeResources()`의 terminal invocation이 선언된 close
  grace 안에 반환한다는 adapter 책임을 포함하며, base의 bounded wrapper가 이를
  runtime에서 감시한다. 이 책임을 증명할 수 없는 adapter는
  `UNKNOWN`/`supported = false`로 선언한다. observer도 동일한 parent deadline과
  단일 in-flight/circuit-breaker 경계를 사용해 종료 경로를 막지 않는다. timeout
  뒤 worker가 살아 있는 동안에는 `CLOSED`를 publish하지 않고, worker completion
  callback이 실제 terminal cleanup 후에만 상태를 닫는다. `BOUNDED` capability는
  validator가 반환한 `takeOnce()`와 native `loadValidated()`가 token의 유효
  deadline 안에 취소를 관찰하고 반환한다는 책임까지 포함한다.
  load thread 안에서
  callback이 `close()`를 재진입하면 `IllegalStateException`으로 거절한다.
- base loader는 validator 이전에 monotonic deadline을 가진 cancellation token을
  만들고 validator와 execution에 같은 token을 전달한다. token은 monotonic
  start/timeout과 overflow-safe `remainingNanos()`를 노출한다. validator는 source I/O
  직전에 token을 확인하며, base loader도 validation 직후 다시 확인해 close가
  validation 중 들어온 경우 command 시작을 차단한다. timeout 만료 또는 caller
  thread interrupt는 token의 원자적 `request()`가 bounded cancellation hook을
  정확히 한 번 호출하는 계기다. timeout도 adapter가 `token.check()`를 호출하는
  순간 같은 hook을 실행한다. 취소된 작업은 `CANCELLED` report를 반환하거나, report를
  만들 수 없는 경우 redacted exception을 던진다. 호출자는 `PARTIAL`, `FAILED`,
  `CANCELLED` 결과를 자동 재시도하지 않는다.
- source는 caller 소유다. adapter는 caller-owned stream을 닫지 않으며, 자신이
  생성한 typed validated handle과 staging/session 자원은 성공·실패·취소 모든
  경로에서 정확히 한 번 닫는다.

## progress와 report 불변식

- progress count는 누적(cumulative) 값이며 모두 0 이상이고
  `succeeded + failed <= processed`를 만족한다.
- phase는 `PREPARE → LOAD_VERTEX → LOAD_EDGE → VERIFY → COMPLETE` 순서로만
  진행한다. adapter는 terminal outcome을 담은 COMPLETE event를 report 직전에
  한 번 보낸다. CANCELLED/FAILED도 COMPLETE event의 `outcome`으로 구분한다.
- callback은 `load`를 호출한 thread에서 phase 경계 또는
  `progressInterval` token 경계에만 실행한다. 임의의 batch event는 허용하지
  않으며, record event를 `progressInterval`보다 자주 발생시키지 않고
  listener가 native lock을 획득하거나 장시간 block하지 않도록 한다.
- phase 경계 event도 동일한 callback budget에 포함한다. 전체 상한은
  `min(1,024, 5 + ceil(processed / progressInterval))`이며, 100,000개
  processed와 interval 1,000에서는 최대 105회다. COMPLETE event는 이 고정
  phase 5회에 포함된다. interval 1에서도 load당 1,024회를 넘으면 adapter
  contract 위반으로 redacted failure를 반환한다.
- listener가 예외를 던지면 모든 adapter가 동일하게 native cancellation을
  요청하고, validated handle/resource를 닫은 뒤 그 예외를 caller에게 다시
  던진다. 이 경로는 report를 반환하지 않으며 native 결과는 indeterminate로
  취급하고 자동 재시도를 금지한다. listener 예외를 삼키거나 backend마다
  계속 진행하는 동작은 금지한다.
- `GraphNativeBulkLoadReport`의 `outcome`은 terminal 상태를 명시한다.
  `COMPLETED`는 실패가 없고, `PARTIAL`은 durable success와 하나 이상의 failed
  record 및 retained/omitted failure detail이 함께 있고,
  `FAILED`는 native 작업이 완료되지 않았고 failed count·omitted detail·operation
  failure detail 중 하나가
  있으며, `CANCELLED`는 timeout, interrupt, close 또는 listener failure 취소를 뜻한다.
  `failures`는 생성 시 defensive snapshot으로 반환하며, `failureDetailsLimit`은 0~1,024이고
  `failures.size <= failureDetailsLimit`을 보장한다. `omittedFailureDetails`는
  상세 보관 상한을 초과한 failure event 수다.

load가 반환하는 report는 request의 `operationName`과
`maxFailureDetails`를 그대로 반영해야 한다. `ATOMIC` capability는 `PARTIAL`
outcome을 반환하지 않으며, 완료되지 않은 `ATOMIC` report의 durable count는
0이어야 한다. `failureDetail = NONE` capability는 상세 목록을
채우지 않고 omitted count만 사용할 수 있다. terminal COMPLETE progress의
`outcome`은 report outcome과 같아야 한다. base verifier가 이 terminal
coupling을 검사한다.

## 실패·안전 계약

- invalid request와 capability mismatch는 native command를 실행하기 전에
  실패한다.
- validator, native command, source cleanup, cancellation hook과 close resource의
  일반 예외(`Exception`)는 base loader가 fixed code의 `GraphNativeBulkLoadException`으로
  redacted mapping한다. listener exception은 유일한 caller-owned 예외 경계로
  동일 인스턴스를 primary로 보존하고 cleanup mapping은 suppressed redacted
  exception으로만 추가한다.
- adapter는 native backend의 오류를 stable code와 code의 고정 public message를
  가진 `GraphNativeBulkLoadFailure`로 매핑한다. 오류를 삼키거나 자동으로
  일반 importer로 전환하지 않는다.
- `ATOMIC`이 아닌 guarantee에서는 report의 `failed`와 `failures`를 통해
  partial result 가능성을 명시하고, 이 SPI는 resume token·rollback·retry
  guarantee를 제공하지 않는다. 후속 checkpoint 이슈가 이를 별도로 정의한다.
- URI source는 기본적으로 거절한다. URI를 지원하는 adapter만 명시적 scheme,
  host/private-network, redirect, credential 정책을 capabilities와 문서에
  선언하고 검증한다. file/directory source는 승인된 staging root 아래로
  canonicalize하며 traversal과 symlink escape를 거절한다.
- source kind와 source 값의 불일치, credential이 포함된 URI, 제어문자 또는
  과도하게 긴 operation name은 backend command 전에 거절한다.
- request, progress, report, 예외 메시지에는 인증정보·비밀값·native command,
  URI user-info/query, 서버 경로와 payload fragment를 포함하지 않는다.

## lifecycle 진단과 관찰성

`GraphNativeBulkLoadDiagnosticObserver`는 선택적 bounded observer 경계다. base
loader는 유효한 parent deadline 안에서는 `STARTED`, `COMPLETED`, `FAILED`,
`CANCELLED`, `CLOSED` 이벤트를 동기적으로 발행하며, deadline이 이미 소진된
timeout 진단은 caller를 지연시키지 않는 단일 비동기 dispatch로 보존한다. 각
event는 다음 값만 가진다.

- 고유하고 log-safe한 bounded `diagnosticId` (`diag-` + monotonic sequence).
- capabilities의 log-safe `backend`, request의 비민감 고정 `operationName`.
- 마지막 검증된 phase, monotonic `elapsed`, terminal `outcome`, fixed `code`,
  cancellation reason(취소 event인 경우).

observer 자체의 예외·stack·suppressed는 public 결과에 전달하지 않고 redacted
unknown으로 기록하거나 폐기한다. observer 구현체가 로그를 남길 때는
`KLogging`을 사용하고 diagnostic 값만 구조화해 기록한다. raw source, tenant/request
ID, URI, native command, server path와 throwable은 event와 로그 어느 쪽에도 포함하지
않는다. 실패 시 report가 없더라도 `FAILED`/`CANCELLED` event가 code와 diagnosticId를
남겨 운영자가 backend/phase/elapsed를 상관할 수 있어야 한다.

## 대안과 선택

| 대안 | 결정 | 이유 |
|---|---|---|
| 단일 loader SPI + immutable value model | 채택 | backend 모듈 경계를 보존하고 테스트·문서화가 작다. |
| provider/session/command를 분리한 다중 SPI | 보류 | staging과 transaction lifecycle을 공통화하려면 아직 backend별 근거가 부족하고 API 표면이 커진다. |
| 기존 `GraphBulkImporter` 확장 | 거절 | native command는 record reader/write loop가 아니며 backend-neutral contract와 fallback 오해를 만든다. |

## 호환성과 마이그레이션

기존 `graph-io-core` public API에는 변경이 없다. 새 패키지는 additive이며
현재 backend 모듈이 구현하지 않아도 된다. 후속 adapter 이슈는 각 backend의
source staging·인증·transaction 계약을 별도로 명시하고 이 SPI를 구현한다.

## 테스트 전략과 DoD

- immutable model의 기본값·검증, log-safe operation/backend identifier, timeout과
  default finite deadline,
  `maxFailureDetails` 상한과 capabilities/source-kind mismatch를 검증한다.
- fake loader로 `OPEN/LOADING/CLOSED`, 단일 load, concurrent load/close,
  `CLOSING` cancellation handshake, idempotent close lifecycle을 검증한다.
  base loader가 validator 호출 전에 lifecycle gate를 적용하고, `CLOSED` 또는
  동시 `LOADING` 요청을 validator/command에 전달하지 않는지 확인한다.
- fake validated source에서 첫 `take()`만 성공하고 두 번째 `take()`는
  `IllegalStateException`을 던지는지, `close()`는 여러 번 호출해도
  `closeOnce()`를 한 번만 실행하는지 검증한다. take/close race에서는
  closeOnce가 takeOnce 종료 뒤에 실행되고 close 이후 take가 거절되는지 검증한다.
- prepare/load/progress/report/close lifecycle과 누적 count·phase·terminal
  event 불변식을 검증한다.
- request+capabilities-bound report factory의 zero-record command failure, `COMPLETED` count equality,
  cancellation reason, request/report operation·detail-limit 일치와
  capability/report postcondition(`ATOMIC`, `failureDetail = NONE`)을 검증한다.
- timeout/interrupt/close 취소, 최대 timeout/close grace와 deadline 초과 후 성공
  차단, listener 예외(indeterminate 결과와 resource
  cleanup), caller-owned source close 보존을 검증한다. cancellation hook과
  cleanup hook이 raw 예외를 던져도 redacted exception만 노출되고 listener
  원본 예외가 primary로 유지되는지, interrupted close가 종료 후 interrupt
  status를 복원하는지 검증한다.
- record 수를 알 수 없는 fake adapter가 phase 경계 event만 보내고 임의의
  batch event를 보내지 않으며, 알려진 record 수의 callback 수가
  `min(1,024, 5 + ceil(processed / progressInterval))`를 넘지 않는지 검증한다.
  interval 1, 느린 listener, timeout/close 경합에서 절대 callback 상한과
  close grace를 검증한다.
- fixed failure-code mapping, request source를 숨기는 `toString`, secret-free public messages,
  partial/failed/cancelled report와 bounded
  failure detail을 검증한다. 대량 fake failure를 사용해 보관 상세가 상한을
  넘지 않는지 확인한다. 100,000개 synthetic failure에서
  `failed = 100_000`, `failures.size = 128`, `omittedFailureDetails = 99_872`를
  pass/fail 기준으로 사용한다.
- unsupported loader가 capability를 `supported = false`로 노출하고 fallback
  없이 예외를 던지는지 검증한다.
- fake source validator가 credential, disallowed scheme/host와 traversal sentinel을
  adapter command에 도달하기 전에 거절하는 contract negative test를 남긴다.
  validator 이전에는 source open/stringification/logging/staging/command 생성이
  0회이고, R과 V 타입이 분리되며 validated handle만 사용하고 정확히 한 번
  닫는지 검증한다. exact origin의 scheme/host/port 조합, URI length와
  redirect-hop bound, BACKEND_SERVER의 origin/artifact revalidation 요구를
  검증한다. 실제 URI 정규화, redirect/DNS rebinding, symlink/TOCTOU와
  backend-server artifact 재검증은 후속 adapter contract suite의 범위로
  명시한다.
- 이 이슈에는 실제 backend command가 없으므로 round-trip throughput 및
  Testcontainers benchmark는 N/A다. 후속 adapter 이슈에서
  backend별 URI/staging, lock, cancellation, throughput과 allocation을
  검증한다. 대신 core test는 bounded failure/progress model overhead와
  100,000개 event stress를 재현하며, 100,000개 processed fake load에서
  `progressInterval = 1,000`일 때 전체 progress callback을 최대 105회로
  제한하고 모든 callback의 thread identity가 caller와 같음을 acceptance
  threshold로 삼는다.
- `:bluetape4k-graph-io-core:test`, `:bluetape4k-graph-io-core:compileKotlin`,
  `git diff --check`를 통과한다.
- 양쪽 README에 native SPI가 실제 backend adapter가 아님을 명시한다.
- #234의 후속 구현 경계와 TinkerPop 비적용을 기록한다.

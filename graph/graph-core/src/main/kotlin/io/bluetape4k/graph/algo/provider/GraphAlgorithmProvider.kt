package io.bluetape4k.graph.algo.provider

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Native provider가 선택할 수 있는 portable graph algorithm 식별자다.
 *
 * 이 목록은 provider capability와 실행 관찰에만 사용한다. 각 provider의
 * 실제 driver 호출과 결과 변환은 선택 모듈이 소유하며 `graph-core`에는
 * native SDK 의존성을 추가하지 않는다.
 */
enum class GraphAlgorithmId {
    PAGE_RANK,
    CONNECTED_COMPONENTS,
    BFS,
    DFS,
}

/** native provider 선택 정책이다. */
enum class GraphAlgorithmProviderPolicy {
    /** 지원하는 native provider가 있으면 선택하고, 없으면 JVM fallback을 사용한다. */
    AUTO,

    /** native provider를 사용하지 않고 JVM fallback을 명시적으로 선택한다. */
    JVM_ONLY,

    /** native provider가 없거나 알고리즘을 지원하지 않으면 예외를 발생시킨다. */
    NATIVE_ONLY,
}

/** 한 provider가 제공하는 algorithm capability의 불변 설명이다. */
data class GraphAlgorithmProviderDescriptor(
    val id: String,
    val algorithms: Set<GraphAlgorithmId>,
    val version: String? = null,
    val constraints: Set<String> = emptySet(),
): Serializable {

    init {
        id.requireNotBlank("id")
        require(algorithms.isNotEmpty()) { "algorithms must not be empty" }
        version?.requireNotBlank("version")
        constraints.forEach { it.requireNotBlank("constraints") }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 선택 모듈이 구현하는 backend-neutral native algorithm provider SPI다.
 *
 * 이 SPI는 capability와 선택 경계만 정의한다. provider 모듈은 자신의
 * backend driver를 통해 결과를 계산하고, 이 모듈은 native SDK를 직접
 * 참조하지 않는다.
 */
interface GraphAlgorithmProvider {
    val descriptor: GraphAlgorithmProviderDescriptor
}

/** 알고리즘이 실제로 선택된 실행 경로다. */
enum class GraphAlgorithmExecutionPath {
    NATIVE,
    JVM_FALLBACK,
}

/** JVM fallback을 선택한 이유다. */
enum class GraphAlgorithmFallbackReason {
    NO_PROVIDER,
    NO_SUPPORTED_PROVIDER,
    JVM_ONLY_POLICY,
}

/**
 * 한 알고리즘 호출에서 선택된 provider와 경로를 기록한다.
 *
 * `JVM_FALLBACK`인 실행은 반드시 이유를 함께 기록한다. Native 실행은
 * fallback 이유를 가질 수 없으며, native 실행 실패를 이 타입으로 바꾸어
 * 조용히 fallback해서는 안 된다.
 */
data class GraphAlgorithmExecution(
    val algorithm: GraphAlgorithmId,
    val providerId: String,
    val path: GraphAlgorithmExecutionPath,
    val fallbackReason: GraphAlgorithmFallbackReason? = null,
): Serializable {

    init {
        providerId.requireNotBlank("providerId")
        when (path) {
            GraphAlgorithmExecutionPath.NATIVE ->
                require(fallbackReason == null) { "native execution cannot have a fallback reason" }

            GraphAlgorithmExecutionPath.JVM_FALLBACK ->
                require(fallbackReason != null) { "JVM fallback execution requires a reason" }
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 알고리즘 실행 경로를 관찰하는 callback이다. */
fun interface GraphAlgorithmExecutionObserver {
    fun onExecution(execution: GraphAlgorithmExecution)

    companion object {
        /** 관찰하지 않을 때 사용하는 no-op observer다. */
        val Noop: GraphAlgorithmExecutionObserver = GraphAlgorithmExecutionObserver { }
    }
}

/** 마지막으로 관찰된 알고리즘 실행 경로를 노출하는 backend 표면이다. */
interface GraphAlgorithmExecutionObservable {
    val lastAlgorithmExecution: GraphAlgorithmExecution?
}

/** native-only 정책에서 provider capability가 부족할 때 발생한다. */
class GraphAlgorithmProviderUnavailableException(
    message: String,
): UnsupportedOperationException(message)

/**
 * provider capability를 실행 정책으로 변환하는 공통 selector다.
 *
 * 실제 native provider가 없는 현재 backend는 빈 provider 목록으로 호출해
 * JVM fallback과 그 이유를 관찰한다. 선택 모듈은 자신의 descriptor를
 * 전달해 native 선택을 검증할 수 있지만, 이 selector가 native 실행을
 * 대신하지는 않는다.
 */
object GraphAlgorithmProviderSelector {
    /** 현재 portable JVM 구현을 나타내는 관찰용 provider ID다. */
    const val JVM_PROVIDER_ID: String = "jvm-fallback"

    /**
     * 주어진 policy와 capability에 따라 실행 경로를 선택한다.
     *
     * @throws GraphAlgorithmProviderUnavailableException `NATIVE_ONLY`인데
     * provider가 없거나 알고리즘을 지원하지 않을 때.
     */
    fun select(
        algorithm: GraphAlgorithmId,
        providers: Iterable<GraphAlgorithmProvider> = emptyList(),
        policy: GraphAlgorithmProviderPolicy = GraphAlgorithmProviderPolicy.AUTO,
    ): GraphAlgorithmExecution = when (policy) {
        GraphAlgorithmProviderPolicy.JVM_ONLY ->
            fallback(algorithm, GraphAlgorithmFallbackReason.JVM_ONLY_POLICY)

        GraphAlgorithmProviderPolicy.AUTO,
        GraphAlgorithmProviderPolicy.NATIVE_ONLY,
        -> {
            val providerList = providers.toList()
            val provider = providerList.firstOrNull { algorithm in it.descriptor.algorithms }
            when {
                provider != null -> GraphAlgorithmExecution(
                    algorithm = algorithm,
                    providerId = provider.descriptor.id,
                    path = GraphAlgorithmExecutionPath.NATIVE,
                )

                policy == GraphAlgorithmProviderPolicy.NATIVE_ONLY -> {
                    val reason = if (providerList.isEmpty()) {
                        "no provider is configured"
                    } else {
                        "no provider supports $algorithm"
                    }
                    throw GraphAlgorithmProviderUnavailableException(
                        "Native provider is required for $algorithm, but $reason",
                    )
                }

                else -> fallback(
                    algorithm,
                    if (providerList.isEmpty()) {
                        GraphAlgorithmFallbackReason.NO_PROVIDER
                    } else {
                        GraphAlgorithmFallbackReason.NO_SUPPORTED_PROVIDER
                    },
                )
            }
        }
    }

    private fun fallback(
        algorithm: GraphAlgorithmId,
        reason: GraphAlgorithmFallbackReason,
    ): GraphAlgorithmExecution = GraphAlgorithmExecution(
        algorithm = algorithm,
        providerId = JVM_PROVIDER_ID,
        path = GraphAlgorithmExecutionPath.JVM_FALLBACK,
        fallbackReason = reason,
    )
}

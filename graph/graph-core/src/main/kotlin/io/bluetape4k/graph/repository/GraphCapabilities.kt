package io.bluetape4k.graph.repository

import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSuspendSchemaManagementOperations
import java.io.Serializable

/**
 * 그래프 backend가 제공하는 선택적 기능의 공통 식별자다.
 *
 * `GRAPH_ALGORITHM`은 모든 graph facade가 제공하는 portable JVM 알고리즘을
 * 의미하며, backend 확장 모듈이 제공하는 기능은 `NATIVE_ALGORITHM`으로
 * 별도 표시한다.
 */
enum class GraphCapability {
    /** MERGE/upsert API를 제공한다. */
    MERGE,

    /** index/constraint schema 관리 API를 제공한다. */
    SCHEMA,

    /** backend transaction DSL을 제공한다. */
    TRANSACTION,

    /** 여러 vertex/edge를 한 번에 생성하는 batch API를 제공한다. */
    BATCH_INSERT,

    /** vertex/edge를 chunk 단위로 조회하는 API를 제공한다 (source bounded 보장은 별도 capability). */
    CHUNKED_READ,

    /** chunk 단위로 graph 데이터를 내보내는 API를 제공한다 (source bounded 보장은 별도 capability). */
    CHUNKED_EXPORT,

    /** 전체 결과를 먼저 materialize하지 않는 bounded vertex/edge 조회를 제공한다. */
    BOUNDED_CHUNKED_READ,

    /** 전체 결과를 먼저 materialize하지 않는 bounded graph export를 제공한다. */
    BOUNDED_CHUNKED_EXPORT,

    /** 가중치 옵션을 받는 portable path API를 제공한다. */
    WEIGHTED_PATH,

    /** portable JVM graph algorithm API를 제공한다. */
    GRAPH_ALGORITHM,

    /** backend-native algorithm provider를 제공한다. */
    NATIVE_ALGORITHM,
}

/**
 * 한 graph facade에서 조회한 capability 집합과 각 capability의 제약을 담는다.
 *
 * `supported`에 포함되지 않은 capability는 지원되지 않으며, 지원 여부를
 * 추측해 unsupported 호출을 시도해서는 안 된다. `versions`와 `constraints`는
 * 지원 capability에 대해서만 기록할 수 있다.
 */
data class GraphCapabilities(
    val supported: Set<GraphCapability>,
    val versions: Map<GraphCapability, String> = emptyMap(),
    val constraints: Map<GraphCapability, Set<String>> = emptyMap(),
): Serializable {

    init {
        require(versions.keys.all(supported::contains)) {
            "versions may describe only supported capabilities"
        }
        require(constraints.keys.all(supported::contains)) {
            "constraints may describe only supported capabilities"
        }
    }

    /** 주어진 capability가 지원되는지 반환한다. */
    fun supports(capability: GraphCapability): Boolean = capability in supported

    /** 주어진 capability의 계약 버전을 반환한다. */
    fun version(capability: GraphCapability): String? = versions[capability]

    /** 주어진 capability의 실행 제약을 반환한다. */
    fun constraints(capability: GraphCapability): Set<String> = constraints[capability].orEmpty()

    companion object {
        private const val serialVersionUID: Long = 1L
        private const val CORE_API_VERSION: String = "core-0.7"

        internal fun from(operation: Any): GraphCapabilities {
            val supported = buildSet {
                when (operation) {
                    is GraphMergeOperations, is GraphSuspendMergeOperations -> add(GraphCapability.MERGE)
                }
                when (operation) {
                    is GraphSchemaManagementOperations, is GraphSuspendSchemaManagementOperations ->
                        add(GraphCapability.SCHEMA)
                }
                when (operation) {
                    is GraphTransactionalOperations, is GraphSuspendTransactionalOperations ->
                        add(GraphCapability.TRANSACTION)
                }
                when (operation) {
                    is GraphVertexRepository,
                    is GraphEdgeRepository,
                    is GraphSuspendVertexRepository,
                    is GraphSuspendEdgeRepository,
                    is GraphVirtualThreadVertexRepository,
                    -> {
                        add(GraphCapability.BATCH_INSERT)
                        add(GraphCapability.CHUNKED_EXPORT)
                        add(GraphCapability.CHUNKED_READ)
                        if (operation is GraphBoundedChunkOperations) {
                            add(GraphCapability.BOUNDED_CHUNKED_READ)
                            add(GraphCapability.BOUNDED_CHUNKED_EXPORT)
                        }
                    }
                }
                when (operation) {
                    is GraphTraversalRepository,
                    is GraphSuspendTraversalRepository,
                    is GraphVirtualThreadTraversalRepository,
                    -> add(GraphCapability.WEIGHTED_PATH)
                }
                when (operation) {
                    is GraphGenericRepository,
                    is GraphSuspendGenericRepository,
                    is GraphVirtualThreadAlgorithmRepository,
                    -> add(GraphCapability.GRAPH_ALGORITHM)
                }
                if (operation is GraphNativeAlgorithmOperations) {
                    add(GraphCapability.NATIVE_ALGORITHM)
                }
            }

            return GraphCapabilities(
                supported = supported,
                versions = supported.associateWith { CORE_API_VERSION },
                constraints = supported.associateWith(::defaultConstraints),
            )
        }

        private fun defaultConstraints(capability: GraphCapability): Set<String> = when (capability) {
            GraphCapability.MERGE -> setOf("backend-native-or-atomic-upsert")
            GraphCapability.SCHEMA -> setOf("backend-schema-manager")
            GraphCapability.TRANSACTION -> setOf("backend-transaction-scope")
            GraphCapability.BATCH_INSERT -> setOf("ordered-batch-result")
            GraphCapability.CHUNKED_READ -> setOf("positive-chunk-size", "api-chunking-only")
            GraphCapability.CHUNKED_EXPORT -> setOf("positive-chunk-size", "api-chunking-only")
            GraphCapability.BOUNDED_CHUNKED_READ -> setOf("positive-chunk-size", "native-traversal-bounded")
            GraphCapability.BOUNDED_CHUNKED_EXPORT -> setOf("positive-chunk-size", "native-traversal-bounded")
            GraphCapability.WEIGHTED_PATH -> setOf("weight-property-or-unit-weight")
            GraphCapability.GRAPH_ALGORITHM -> setOf("portable-jvm-semantics")
            GraphCapability.NATIVE_ALGORITHM -> setOf("provider-declared")
        }
    }
}

/**
 * 명시적인 capability 매핑을 제공하는 동기 decorator용 SPI다.
 *
 * `GraphOperations by delegate`만으로는 delegate의 marker interface가 보존되지
 * 않으므로, capability를 보존하는 decorator는 이 SPI를 구현해야 한다.
 */
interface GraphCapabilitiesOperations {
    /** 실제 delegate의 capability 매핑을 반환한다. */
    fun capabilities(): GraphCapabilities
}

/** coroutine decorator가 capability 매핑을 명시적으로 보존하기 위한 SPI다. */
interface GraphSuspendCapabilitiesOperations {
    /** 실제 delegate의 capability 매핑을 반환한다. */
    suspend fun capabilities(): GraphCapabilities
}

/** Virtual Thread decorator가 capability 매핑을 보존하기 위한 SPI다. */
interface GraphVirtualThreadCapabilitiesOperations {
    /** 실제 동기 delegate의 capability 매핑을 반환한다. */
    fun capabilities(): GraphCapabilities
}

/** backend-native algorithm provider가 자신이 지원하는 capability를 표시하는 SPI다. */
interface GraphNativeAlgorithmOperations

/**
 * source 조회가 전체 label 결과를 먼저 materialize하지 않고 chunk 경계를 지키는
 * backend 구현을 표시하는 marker다.
 *
 * `CHUNKED_*` API만 제공하는 기본 repository 구현에는 이 marker를 추가하지 않는다.
 */
interface GraphBoundedChunkOperations

/** 동기 graph facade의 capability를 조회한다. */
fun GraphOperations.capabilities(): GraphCapabilities =
    (this as? GraphCapabilitiesOperations)?.capabilities() ?: GraphCapabilities.from(this)

/** coroutine graph facade의 capability를 조회한다. */
suspend fun GraphSuspendOperations.capabilities(): GraphCapabilities =
    (this as? GraphSuspendCapabilitiesOperations)?.capabilities() ?: GraphCapabilities.from(this)

/** Virtual Thread graph facade의 capability를 조회한다. */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun GraphVirtualThreadOperations.capabilities(): GraphCapabilities =
    (this as? GraphVirtualThreadCapabilitiesOperations)?.capabilities() ?: GraphCapabilities.from(this)

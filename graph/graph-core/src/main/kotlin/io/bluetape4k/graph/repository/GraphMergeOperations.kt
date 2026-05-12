package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank

/**
 * 검증을 통과한 merge/upsert 속성 묶음.
 *
 * `matchProperties`는 요소를 찾는 안정적인 식별자이고, `setProperties`는 생성되었거나
 * 이미 존재하는 요소에 적용할 갱신 속성이다.
 *
 * ## 동작/계약
 * - `matchProperties`의 값은 `null`일 수 없다. null identity key는 backend별 query 의미가
 *   달라질 수 있기 때문이다.
 * - `setProperties`는 `matchProperties`와 같은 key를 덮어쓸 수 없다.
 *
 * ```kotlin
 * val props = GraphMergeValidation.validateVertex(
 *     label = "Person",
 *     matchProperties = mapOf("email" to "alice@example.com"),
 *     setProperties = mapOf("name" to "Alice"),
 * )
 * ```
 */
data class GraphMergeProperties(
    val matchProperties: Map<String, Any?>,
    val setProperties: Map<String, Any?>,
)

/**
 * merge/upsert API가 모든 백엔드에서 공유하는 입력 검증 규칙.
 *
 * 백엔드 구현체는 쿼리 문자열을 만들기 전에 이 검증을 먼저 호출해야 한다.
 *
 * ## 동작/계약
 * - label과 property key는 backend query에 안전한 identifier여야 한다.
 * - vertex merge는 [validateVertex]에서 non-empty `matchProperties`를 요구한다.
 * - edge merge는 endpoint id와 label이 identity를 이루므로 empty `matchProperties`를 허용한다.
 *
 * ```kotlin
 * val validated = GraphMergeValidation.validateEdge(
 *     fromId = GraphElementId("1"),
 *     toId = GraphElementId("2"),
 *     label = "KNOWS",
 *     matchProperties = emptyMap(),
 *     setProperties = mapOf("since" to 2026),
 * )
 * ```
 */
object GraphMergeValidation {

    /**
     * 정점 merge 입력을 검증한다.
     *
     * 정점은 레이블만으로 merge 하면 여러 기존 정점이 매칭될 수 있으므로
     * `matchProperties`가 비어 있으면 거부한다.
     */
    fun validateVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphMergeProperties {
        validateLabel(label)
        require(matchProperties.isNotEmpty()) {
            "matchProperties must not be empty for mergeVertex."
        }
        return validateProperties(matchProperties, setProperties)
    }

    /**
     * 간선 merge 입력을 검증한다.
     *
     * 간선은 시작 정점 ID, 종료 정점 ID, 레이블이 기본 식별자이므로
     * `matchProperties`가 비어 있어도 허용한다.
     */
    fun validateEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphMergeProperties {
        fromId.value.requireNotBlank("fromId.value")
        toId.value.requireNotBlank("toId.value")
        validateLabel(label)
        return validateProperties(matchProperties, setProperties)
    }

    private fun validateLabel(label: String) {
        label.requireNotBlank("label").requireSafeIdentifier("label")
    }

    private fun validateProperties(
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphMergeProperties {
        matchProperties.forEach { (key, value) ->
            key.requireNotBlank("match property key").requireSafeIdentifier("match property key")
            require(value != null) {
                "matchProperties must not contain null values: $key"
            }
        }
        setProperties.keys.forEach { key ->
            key.requireNotBlank("set property key").requireSafeIdentifier("set property key")
        }

        val overlap = matchProperties.keys.intersect(setProperties.keys)
        require(overlap.isEmpty()) {
            "setProperties must not overwrite matchProperties keys: ${overlap.joinToString(", ")}"
        }

        return GraphMergeProperties(matchProperties, setProperties)
    }
}

/**
 * 동기 그래프 구현체가 merge/upsert 기능을 제공할 때 구현하는 capability interface.
 *
 * [GraphOperations] 자체에 멤버를 추가하지 않아 기존 구현체와 테스트 fake의 source compatibility를 유지한다.
 *
 * ## 동작/계약
 * - 구현체는 [GraphMergeValidation]으로 입력을 검증한 뒤 backend-native `MERGE` 또는
 *   transactional match/update/create path를 사용해야 한다.
 * - unsupported backend는 이 interface를 구현하지 않고 extension function에서
 *   [UnsupportedOperationException]으로 fail fast 하도록 둔다.
 *
 * ```kotlin
 * val alice = ops.mergeVertex(
 *     label = "Person",
 *     matchProperties = mapOf("email" to "alice@example.com"),
 *     setProperties = mapOf("name" to "Alice"),
 * )
 * ```
 */
interface GraphMergeOperations {

    /**
     * `matchProperties`로 정점을 찾고, 없으면 생성한 뒤 `setProperties`를 적용해 반환한다.
     */
    fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphVertex

    /**
     * 시작/종료 정점, 간선 레이블, `matchProperties`로 간선을 찾고, 없으면 생성한 뒤
     * `setProperties`를 적용해 반환한다.
     */
    fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?> = emptyMap(),
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphEdge
}

/**
 * 코루틴 그래프 구현체가 merge/upsert 기능을 제공할 때 구현하는 capability interface.
 *
 * ## 동작/계약
 * - [GraphMergeOperations]와 같은 identity/set semantics를 suspend API로 제공한다.
 * - cancellation은 backend implementation에서 삼키지 않아야 한다.
 *
 * ```kotlin
 * val alice = suspendOps.mergeVertex(
 *     label = "Person",
 *     matchProperties = mapOf("email" to "alice@example.com"),
 *     setProperties = mapOf("name" to "Alice"),
 * )
 * ```
 */
interface GraphSuspendMergeOperations {

    /** `matchProperties`로 정점을 찾고, 없으면 생성한 뒤 `setProperties`를 적용해 반환한다. */
    suspend fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphVertex

    /** 시작/종료 정점, 간선 레이블, `matchProperties`로 간선을 찾고, 없으면 생성한 뒤 `setProperties`를 적용한다. */
    suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?> = emptyMap(),
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphEdge
}

/**
 * [GraphOperations]에서 정점 merge/upsert를 실행한다.
 *
 * 구현체가 [GraphMergeOperations]를 구현하지 않으면 read-then-write fallback을 사용하지 않고
 * 명시적으로 [UnsupportedOperationException]을 던진다.
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.mergeVertex
 *
 * val vertex = ops.mergeVertex(
 *     "Person",
 *     matchProperties = mapOf("email" to "alice@example.com"),
 *     setProperties = mapOf("name" to "Alice"),
 * )
 * ```
 */
fun GraphOperations.mergeVertex(
    label: String,
    matchProperties: Map<String, Any?>,
    setProperties: Map<String, Any?> = emptyMap(),
): GraphVertex {
    val merge = this as? GraphMergeOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support graph merge operations."
        )
    return merge.mergeVertex(label, matchProperties, setProperties)
}

/**
 * [GraphOperations]에서 간선 merge/upsert를 실행한다.
 *
 * 구현체가 [GraphMergeOperations]를 구현하지 않으면 명시적으로 [UnsupportedOperationException]을 던진다.
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.mergeEdge
 *
 * val edge = ops.mergeEdge(
 *     fromId = alice.id,
 *     toId = bob.id,
 *     label = "KNOWS",
 *     setProperties = mapOf("since" to 2026),
 * )
 * ```
 */
fun GraphOperations.mergeEdge(
    fromId: GraphElementId,
    toId: GraphElementId,
    label: String,
    matchProperties: Map<String, Any?> = emptyMap(),
    setProperties: Map<String, Any?> = emptyMap(),
): GraphEdge {
    val merge = this as? GraphMergeOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support graph merge operations."
        )
    return merge.mergeEdge(fromId, toId, label, matchProperties, setProperties)
}

/**
 * [GraphSuspendOperations]에서 코루틴 정점 merge/upsert를 실행한다.
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.mergeVertex
 *
 * val vertex = suspendOps.mergeVertex(
 *     "Person",
 *     matchProperties = mapOf("email" to "alice@example.com"),
 * )
 * ```
 */
suspend fun GraphSuspendOperations.mergeVertex(
    label: String,
    matchProperties: Map<String, Any?>,
    setProperties: Map<String, Any?> = emptyMap(),
): GraphVertex {
    val merge = this as? GraphSuspendMergeOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support suspend graph merge operations."
        )
    return merge.mergeVertex(label, matchProperties, setProperties)
}

/**
 * [GraphSuspendOperations]에서 코루틴 간선 merge/upsert를 실행한다.
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.mergeEdge
 *
 * val edge = suspendOps.mergeEdge(alice.id, bob.id, "KNOWS")
 * ```
 */
suspend fun GraphSuspendOperations.mergeEdge(
    fromId: GraphElementId,
    toId: GraphElementId,
    label: String,
    matchProperties: Map<String, Any?> = emptyMap(),
    setProperties: Map<String, Any?> = emptyMap(),
): GraphEdge {
    val merge = this as? GraphSuspendMergeOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support suspend graph merge operations."
        )
    return merge.mergeEdge(fromId, toId, label, matchProperties, setProperties)
}

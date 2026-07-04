package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank

/**
 * Validated merge/upsert property bundle.
 *
 * `matchProperties` are stable identifiers used to find an element, while `setProperties`
 * are updates applied to a newly created or existing element.
 *
 * ## Contract
 * - `matchProperties` values must not be `null`; null identity values can have
 *   backend-specific query semantics.
 * - `setProperties` must not overwrite keys from `matchProperties`.
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
 * Shared input validation rules for backend merge/upsert APIs.
 *
 * Backend implementations should call this validator before building query strings.
 *
 * ## Contract
 * - Labels and property keys must be backend-query-safe identifiers.
 * - Vertex merges require non-empty `matchProperties` through [validateVertex].
 * - Edge merges allow empty `matchProperties` because endpoint IDs and label form the identity.
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
     * Validates vertex merge input.
     *
     * Vertices reject empty `matchProperties` because merging by label alone can match
     * multiple existing vertices.
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
     * Validates edge merge input.
     *
     * Edges allow empty `matchProperties` because start vertex ID, end vertex ID,
     * and label form the primary identity.
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
 * Capability interface for synchronous graph implementations that support merge/upsert.
 *
 * This keeps source compatibility for existing implementations and test fakes by
 * avoiding new members on [GraphOperations].
 *
 * ## Contract
 * - Implementations should validate input with [GraphMergeValidation], then use a
 *   backend-native `MERGE` or transactional match/update/create path.
 * - Unsupported backends should not implement this interface; extension functions
 *   then fail fast with [UnsupportedOperationException].
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
     * Finds a vertex by `matchProperties`, creates it when absent, applies `setProperties`, and returns it.
     */
    fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphVertex

    /**
     * Finds an edge by endpoints, label, and `matchProperties`, creates it when absent,
     * applies `setProperties`, and returns it.
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
 * Capability interface for coroutine graph implementations that support merge/upsert.
 *
 * ## Contract
 * - Provides the same identity/set semantics as [GraphMergeOperations] through suspend APIs.
 * - Backend implementations must not swallow cancellation.
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

    /** Finds a vertex by `matchProperties`, creates it when absent, applies `setProperties`, and returns it. */
    suspend fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphVertex

    /** Finds or creates an edge by endpoints, label, and `matchProperties`, then applies `setProperties`. */
    suspend fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?> = emptyMap(),
        setProperties: Map<String, Any?> = emptyMap(),
    ): GraphEdge
}

/**
 * Executes vertex merge/upsert on [GraphOperations].
 *
 * If the implementation does not implement [GraphMergeOperations], this function
 * explicitly throws [UnsupportedOperationException] instead of using a read-then-write fallback.
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
 * Executes edge merge/upsert on [GraphOperations].
 *
 * If the implementation does not implement [GraphMergeOperations], this function
 * explicitly throws [UnsupportedOperationException].
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
 * Executes coroutine vertex merge/upsert on [GraphSuspendOperations].
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
 * Executes coroutine edge merge/upsert on [GraphSuspendOperations].
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

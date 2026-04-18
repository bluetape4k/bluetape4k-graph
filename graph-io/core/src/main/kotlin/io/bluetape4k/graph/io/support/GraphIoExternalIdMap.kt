package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId

/**
 * 외부 ID와 백엔드가 발급한 GraphElementId 간의 매핑.
 * `DuplicateVertexPolicy`에 따라 중복 정책을 강제한다.
 */
internal class GraphIoExternalIdMap(
    private val duplicatePolicy: DuplicateVertexPolicy,
) {
    private val mapping = HashMap<String, GraphElementId>()

    enum class PutResult { CREATED, SKIPPED }

    fun contains(externalId: String): Boolean = mapping.containsKey(externalId)

    fun put(externalId: String, backendId: GraphElementId) {
        mapping[externalId] = backendId
    }

    fun putFirstOrFail(externalId: String, backendId: GraphElementId): PutResult {
        val existing = mapping[externalId]
        if (existing == null) {
            mapping[externalId] = backendId
            return PutResult.CREATED
        }
        return when (duplicatePolicy) {
            DuplicateVertexPolicy.FAIL -> error("Duplicate vertex externalId='$externalId'")
            DuplicateVertexPolicy.SKIP -> PutResult.SKIPPED
        }
    }

    fun resolve(externalId: String): GraphElementId? = mapping[externalId]
    fun size(): Int = mapping.size
}

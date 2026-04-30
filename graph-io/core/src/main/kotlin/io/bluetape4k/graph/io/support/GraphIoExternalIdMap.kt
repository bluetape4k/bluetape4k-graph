package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId

/**
 * 외부 ID와 백엔드가 발급한 GraphElementId 간의 매핑.
 * `DuplicateVertexPolicy`에 따라 중복 정책을 강제한다.
 */
class GraphIoExternalIdMap(
    private val duplicatePolicy: DuplicateVertexPolicy,
) {
    private val mapping = HashMap<String, GraphElementId>()

    enum class PutResult { CREATED, SKIPPED }

    fun contains(externalId: String): Boolean = mapping.containsKey(externalId)

    /**
     * 이미 [putFirstOrFail]로 등록된 외부 ID의 백엔드 ID를 덮어쓴다.
     *
     * 임포터의 2단계 패턴에서 사용된다:
     * 1. [putFirstOrFail] — 중복 정책 게이트 통과 후 임시 ID를 등록한다.
     * 2. [put] — 백엔드가 실제 ID를 발급한 뒤 최종 ID로 교체한다.
     *
     * 이 메서드는 [putFirstOrFail] 이후에만 호출해야 하며, 신규 삽입용으로 사용하면 안 된다.
     */
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

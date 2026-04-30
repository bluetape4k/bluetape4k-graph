package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId

/**
 * 외부 ID와 백엔드가 발급한 [GraphElementId] 간의 매핑.
 * [DuplicateVertexPolicy]에 따라 중복 정책을 강제한다.
 *
 * 임포트 작업 중 외부 소스의 ID를 백엔드 그래프 DB가 발급한 실제 ID에 매핑하기 위해 사용된다.
 * 단일 스레드 임포트 컨텍스트에서만 사용해야 한다.
 *
 * @param duplicatePolicy 동일 외부 ID가 두 번 이상 등장할 때의 처리 정책.
 */
class GraphIoExternalIdMap(
    private val duplicatePolicy: DuplicateVertexPolicy,
) {
    private val mapping = HashMap<String, GraphElementId>()

    /**
     * [putFirstOrFail] 결과: [CREATED]는 신규 삽입, [SKIPPED]는 중복 정책에 의해 건너뜀.
     */
    enum class PutResult { CREATED, SKIPPED }

    /**
     * 주어진 [externalId]가 이미 등록되어 있는지 확인한다.
     *
     * @param externalId 조회할 외부 ID 문자열.
     * @return 등록된 경우 `true`, 미등록 시 `false`.
     */
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

    /**
     * [externalId]에 매핑된 백엔드 [GraphElementId]를 반환한다.
     *
     * @param externalId 조회할 외부 ID 문자열.
     * @return 등록된 백엔드 ID, 미등록 시 `null`.
     */
    fun resolve(externalId: String): GraphElementId? = mapping[externalId]

    /**
     * 현재 등록된 외부 ID 수를 반환한다.
     *
     * @return 외부 ID 기준 매핑 항목 수.
     */
    fun size(): Int = mapping.size
}

package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 외부 ID와 백엔드가 발급한 [GraphElementId] 간의 매핑.
 * [DuplicateVertexPolicy]에 따라 중복 정책을 강제한다.
 *
 * 임포트 작업 중 외부 소스의 ID를 백엔드 그래프 DB가 발급한 실제 ID에 매핑하기 위해 사용된다.
 * 단일 스레드 임포트 컨텍스트에서만 사용해야 한다.
 *
 * ### 사용 예제 — 임포터의 2단계 패턴
 *
 * ```kotlin
 * val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.SKIP)
 *
 * for (record in vertexRecords) {
 *     val externalId = record.id
 *     // 1) 중복 정책 게이트 — 임시 ID(externalId 자체)로 등록
 *     val result = idMap.putFirstOrFail(externalId, GraphElementId(externalId))
 *     if (result == GraphIoExternalIdMap.PutResult.SKIPPED) continue
 *
 *     // 2) 백엔드에서 실제 ID 발급 후 매핑 갱신
 *     val created = operations.createVertex(record.label, record.properties)
 *     idMap.put(externalId, created.id)
 * }
 *
 * // 엣지 패스에서 외부 ID → 실제 백엔드 ID 해소
 * val backendId = idMap.resolve(externalIdFromEdge)
 * ```
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
     *
     * @throws IllegalStateException [externalId]가 [putFirstOrFail]로 등록되지 않은 경우.
     *   [DuplicateVertexPolicy]를 우회하는 무결성 위반을 조기에 감지하기 위함.
     */
    fun put(externalId: String, backendId: GraphElementId) {
        check(mapping.containsKey(externalId)) {
            "put('$externalId', ...) called before putFirstOrFail() — duplicate policy bypass risk"
        }
        mapping[externalId] = backendId
    }

    /**
     * 외부 ID를 처음 등록하거나 중복 정책을 적용한다.
     *
     * - 신규 ID: 맵에 삽입하고 [PutResult.CREATED]를 반환한다.
     * - 중복 ID + [DuplicateVertexPolicy.SKIP]: [PutResult.SKIPPED]를 반환한다.
     * - 중복 ID + [DuplicateVertexPolicy.FAIL]: 예외를 던진다.
     *
     * @param externalId 등록할 외부 ID.
     * @param backendId 백엔드가 발급한 초기 ID.
     * @return [PutResult.CREATED] (신규 삽입) 또는 [PutResult.SKIPPED] (중복, Skip 정책).
     * @throws IllegalStateException [DuplicateVertexPolicy.FAIL]이고 동일 [externalId]가 이미 등록된 경우.
     */
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
     * 현재 등록된 외부 ID 수.
     */
    val size: Int get() = mapping.size

    /**
     * graph-io checkpoint에 저장할 결정적이고 불투명한 snapshot을 반환한다.
     * 임의의 외부/backend ID가 레코드 형식을 깨뜨리지 않도록 URL-safe Base64를 사용한다.
     */
    fun snapshot(): String = buildString {
        append(SNAPSHOT_VERSION)
        mapping.entries
            .sortedBy { it.key }
            .forEach { (externalId, backendId) ->
                append(';')
                append(encode(externalId))
                append(':')
                append(encode(backendId.value))
            }
    }

    /** 재개 import 전에 checkpoint snapshot을 이 맵으로 복원한다. */
    fun restore(snapshot: String?) {
        if (snapshot.isNullOrBlank()) return
        val entries = snapshot.split(';')
        require(entries.first() == SNAPSHOT_VERSION) { "unsupported external ID map snapshot" }
        mapping.clear()
        entries.drop(1).forEach { entry ->
            if (entry.isBlank()) return@forEach
            val separator = entry.indexOf(':')
            require(separator > 0 && separator < entry.lastIndex) {
                "invalid external ID map snapshot entry"
            }
            val externalId = decode(entry.substring(0, separator))
            val backendId = decode(entry.substring(separator + 1))
            mapping[externalId] = GraphElementId(backendId)
        }
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        const val SNAPSHOT_VERSION = "v1"
    }
}

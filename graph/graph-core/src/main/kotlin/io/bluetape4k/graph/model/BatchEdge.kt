package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 배치 간선 생성 입력 모델.
 *
 * 하나의 [GraphEdgeRepository.createEdges][io.bluetape4k.graph.repository.GraphEdgeRepository.createEdges]
 * 호출은 동일한 간선 레이블을 공유하므로, 이 모델은 양 끝점과 행별 속성만 담는다.
 *
 * ## 동작/계약
 *
 * - [fromId]와 [toId]는 이미 존재하는 정점의 ID여야 한다.
 * - [properties]는 생성할 간선에 저장할 속성 맵이다.
 * - 배치 insert는 merge/upsert가 아니므로 같은 입력을 여러 번 전달하면 중복 간선이 생성될 수 있다.
 *
 * ```kotlin
 * val edge = BatchEdge(
 *     fromId = alice.id,
 *     toId = bob.id,
 *     properties = mapOf("since" to 2024),
 * )
 * ```
 */
data class BatchEdge(
    val fromId: GraphElementId,
    val toId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

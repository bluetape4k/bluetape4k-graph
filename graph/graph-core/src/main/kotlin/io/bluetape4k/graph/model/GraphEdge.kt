package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프의 간선(Edge/Relationship).
 *
 * 두 정점 사이의 관계를 나타내는 불변 모델이다.
 * 방향 그래프에서는 [startId] → [endId] 방향으로 간선이 향한다.
 * `startId == endId`인 자기 참조(self-loop) 간선도 허용된다.
 *
 * @property id 백엔드 독립적인 간선 ID.
 * @property label 관계의 타입을 나타내는 레이블 (예: `"KNOWS"`, `"WORKS_AT"`).
 * @property startId 간선의 시작 정점 ID.
 * @property endId 간선의 종료 정점 ID.
 * @property properties 간선에 첨부된 속성 맵. 값은 `null`을 포함할 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * val edge = GraphEdge(
 *     id = GraphElementId.of("e-1"),
 *     label = "KNOWS",
 *     startId = GraphElementId.of("v-1"),
 *     endId = GraphElementId.of("v-2"),
 *     properties = mapOf("since" to 2023)
 * )
 * ```
 */
data class GraphEdge(
    val id: GraphElementId,
    val label: String,
    val startId: GraphElementId,
    val endId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

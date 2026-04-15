package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프의 정점(Vertex/Node).
 *
 * 모든 그래프 백엔드(AGE, Neo4j, Memgraph, TinkerGraph)에서 공통으로 사용하는
 * 불변 정점 모델이다.
 *
 * @property id 백엔드 독립적인 정점 ID.
 * @property label 정점의 타입을 나타내는 레이블 (예: `"Person"`, `"Company"`).
 * @property properties 정점에 첨부된 속성 맵. 값은 `null`을 포함할 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * val person = GraphVertex(
 *     id = GraphElementId.of("v-1"),
 *     label = "Person",
 *     properties = mapOf("name" to "Alice", "age" to 30)
 * )
 * val copy = person.copy(properties = mapOf("name" to "Bob"))
 * ```
 */
data class GraphVertex(
    val id: GraphElementId,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

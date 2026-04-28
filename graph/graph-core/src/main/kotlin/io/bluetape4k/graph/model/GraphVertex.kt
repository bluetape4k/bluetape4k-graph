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

/**
 * [GraphElementId]와 레이블로 정점을 생성한다.
 *
 * ```kotlin
 * val v = graphVertexOf(GraphElementId.of("v-1"), "Person", mapOf("name" to "Alice"))
 * ```
 *
 * @param id 정점 ID.
 * @param label 정점 레이블.
 * @param properties 정점 속성 맵. 기본값은 빈 맵.
 */
fun graphVertexOf(id: GraphElementId, label: String, properties: Map<String, Any?> = emptyMap()) =
    GraphVertex(id, label, properties)

/**
 * 임의 타입의 ID 값으로 정점을 생성한다.
 *
 * ID 변환에 [graphElementIdOf]를 사용하므로 [GraphElementId]를 그대로 전달해도 이중 변환이 발생하지 않는다.
 *
 * ```kotlin
 * val v = graphVertexOf("v-1", "Person")
 * val v2 = graphVertexOf(42L, "Item", mapOf("name" to "Foo"))
 * ```
 *
 * @param id 정점 ID. [GraphElementId], [Long], 또는 `toString()` 결과를 사용하는 임의 타입.
 * @param label 정점 레이블.
 * @param properties 정점 속성 맵. 기본값은 빈 맵.
 */
fun graphVertexOf(id: Any, label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex =
    graphVertexOf(graphElementIdOf(id), label, properties)

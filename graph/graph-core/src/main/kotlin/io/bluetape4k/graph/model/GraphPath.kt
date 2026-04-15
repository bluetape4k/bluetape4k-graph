package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프 경로의 단계 (정점 또는 간선).
 *
 * 경로는 `[VertexStep, EdgeStep, VertexStep, ...]` 형태로 교차 배치된다.
 *
 * ```kotlin
 * val step: PathStep = PathStep.VertexStep(vertex)
 * val edgeStep: PathStep = PathStep.EdgeStep(edge)
 * ```
 *
 */
sealed class PathStep {
    /**
     * 경로 내 정점 단계.
     *
     * ```kotlin
     * val step = PathStep.VertexStep(GraphVertex(GraphElementId.of("v1"), "Person", mapOf("name" to "Alice")))
     * println(step.vertex.label)  // "Person"
     * ```
     *
     * @property vertex 해당 단계의 정점.
     */
    data class VertexStep(val vertex: GraphVertex): PathStep()

    /**
     * 경로 내 간선 단계.
     *
     * ```kotlin
     * val step = PathStep.EdgeStep(edge)
     * println(step.edge.label)  // "KNOWS"
     * ```
     *
     * @property edge 해당 단계의 간선.
     */
    data class EdgeStep(val edge: GraphEdge): PathStep()
}

/**
 * 그래프 경로.
 *
 * [PathStep] 목록으로 구성되며, `[VertexStep, EdgeStep, VertexStep, ...]`
 * 교차 순서를 따른다. 두 정점 사이의 최단 경로 또는 모든 경로 탐색 결과를 표현한다.
 *
 * @property steps 경로를 구성하는 단계 목록.
 *
 * ### 사용 예제
 * ```kotlin
 * val v1 = GraphVertex(GraphElementId.of("1"), "Person")
 * val v2 = GraphVertex(GraphElementId.of("2"), "Person")
 * val e  = GraphEdge(GraphElementId.of("e1"), "KNOWS", v1.id, v2.id)
 *
 * val path = GraphPath(listOf(
 *     PathStep.VertexStep(v1),
 *     PathStep.EdgeStep(e),
 *     PathStep.VertexStep(v2),
 * ))
 * // path.length == 1, path.vertices.size == 2
 * ```
 */
data class GraphPath(
    val steps: List<PathStep>,
): Serializable {
    /** 경로 내 모든 정점을 순서대로 반환한다. */
    val vertices: List<GraphVertex>
        get() = steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex }

    /** 경로 내 모든 간선을 순서대로 반환한다. */
    val edges: List<GraphEdge>
        get() = steps.filterIsInstance<PathStep.EdgeStep>().map { it.edge }

    /** 경로의 길이 (간선 수). 정점만 있는 경로는 0이다. */
    val length: Int
        get() = edges.size

    /** 경로에 단계가 없으면 `true`를 반환한다. */
    val isEmpty: Boolean
        get() = steps.isEmpty()

    companion object {
        private const val serialVersionUID: Long = 1L

        /** 단계가 없는 빈 경로. 탐색 결과가 없을 때 사용한다. */
        val EMPTY = GraphPath(emptyList())

        /**
         * 정점들만으로 구성된 경로를 만든다 (간선 없음).
         *
         * 주로 단일 정점 경로 또는 인접 정점 목록을 경로로 표현할 때 사용한다.
         *
         * ```kotlin
         * val path = GraphPath.of(alice, bob, carol)
         * println(path.length)  // 3
         * ```
         *
         * @param vertices 경로에 포함할 정점들.
         */
        fun of(vararg vertices: GraphVertex): GraphPath =
            GraphPath(vertices.map { PathStep.VertexStep(it) })
    }
}

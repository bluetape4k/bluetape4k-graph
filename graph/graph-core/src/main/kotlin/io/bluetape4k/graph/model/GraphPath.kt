package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * graph path의 step. vertex 또는 edge 중 하나다.
 *
 * path는 `[VertexStep, EdgeStep, VertexStep, ...]` 순서로 번갈아 구성된다.
 *
 * ```kotlin
 * val step: PathStep = PathStep.VertexStep(vertex)
 * val edgeStep: PathStep = PathStep.EdgeStep(edge)
 * ```
 *
 */
sealed class PathStep {
    /**
     * path 안의 vertex step.
     *
     * ```kotlin
     * val step = PathStep.VertexStep(GraphVertex(GraphElementId.of("v1"), "Person", mapOf("name" to "Alice")))
     * println(step.vertex.label)  // "Person"
     * ```
     *
     * @property vertex 이 step의 vertex.
     */
    data class VertexStep(val vertex: GraphVertex): PathStep()

    /**
     * path 안의 edge step.
     *
     * ```kotlin
     * val step = PathStep.EdgeStep(edge)
     * println(step.edge.label)  // "KNOWS"
     * ```
     *
     * @property edge 이 step의 edge.
     */
    data class EdgeStep(val edge: GraphEdge): PathStep()
}

/**
 * Graph path.
 *
 * `[VertexStep, EdgeStep, VertexStep, ...]` 순서의 [PathStep] value로 구성된다.
 * 두 vertex 사이의 shortest path 또는 all-paths traversal result를 표현한다.
 *
 * @property steps path를 구성하는 step 목록.
 * @property totalWeight 전체 path weight. unweighted traversal은 edge count(hop count)를 기본값으로 사용한다.
 *   Dijkstra/A* results set the actual accumulated cost.
 *
 * ### Usage
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
 * // path.length == 1, path.vertices.size == 2, path.totalWeight == 1.0
 * ```
 */
data class GraphPath(
    val steps: List<PathStep>,
    val totalWeight: Double = steps.filterIsInstance<PathStep.EdgeStep>().size.toDouble(),
): Serializable {
    /** Returns all vertices in path order. */
    val vertices: List<GraphVertex>
        get() = steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex }

    /** Returns all edges in path order. */
    val edges: List<GraphEdge>
        get() = steps.filterIsInstance<PathStep.EdgeStep>().map { it.edge }

    /** Path length as the number of edges. Vertex-only paths have length 0. */
    val length: Int
        get() = edges.size

    /** Returns `true` when the path has no steps. */
    val isEmpty: Boolean
        get() = steps.isEmpty()

    companion object {
        private const val serialVersionUID: Long = 1L

        /** Empty path with no steps. Use when traversal has no result. */
        val EMPTY = GraphPath(emptyList())

        /**
         * edge가 없는 vertex-only path를 생성한다.
         *
         * single-vertex path 또는 인접 vertex들을 path로 표현할 때 사용한다.
         *
         * ```kotlin
         * val path = GraphPath.of(alice, bob, carol)
         * println(path.length)  // 3
         * ```
         *
         * @param vertices path에 포함할 vertex 목록.
         */
        fun of(vararg vertices: GraphVertex): GraphPath =
            GraphPath(vertices.map { PathStep.VertexStep(it) })
    }
}

/**
 * [PathStep] value로 path를 생성한다.
 *
 * ```kotlin
 * val path = graphPathOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1), PathStep.VertexStep(v2))
 * ```
 */
@JvmName("graphPathOfPathSteps")
fun graphPathOf(vararg steps: PathStep): GraphPath = GraphPath(steps.toList())

/**
 * [PathStep] list로 path를 생성한다.
 *
 * ```kotlin
 * val path = graphPathOf(listOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1)))
 * ```
 */
@JvmName("graphPathOfPathStepsList")
fun graphPathOf(steps: List<PathStep>): GraphPath = GraphPath(steps)

/**
 * edge 없이 vertex만으로 path를 생성한다.
 *
 * ```kotlin
 * val path = graphPathOf(v1, v2, v3)
 * println(path.length) // 0 (no edges)
 * ```
 */
@JvmName("graphPathOfGraphVertices")
fun graphPathOf(vararg vertices: GraphVertex): GraphPath = GraphPath(vertices.map { PathStep.VertexStep(it) })

/**
 * Creates a vertex-only path from a vertex list, with no edges.
 *
 * ```kotlin
 * val path = graphPathOf(listOf(v1, v2, v3))
 * ```
 */
@JvmName("graphPathOfGraphVertexList")
fun graphPathOf(vertices: List<GraphVertex>): GraphPath = GraphPath(vertices.map { PathStep.VertexStep(it) })

/**
 * Creates an edge-only path from edges, with no vertices.
 *
 * ```kotlin
 * val path = graphPathOf(e1, e2)
 * println(path.length) // 2
 * ```
 */
@JvmName("graphPathOfGraphEdges")
fun graphPathOf(vararg edges: GraphEdge): GraphPath = GraphPath(edges.map { PathStep.EdgeStep(it) })

/**
 * Creates an edge-only path from an edge list, with no vertices.
 *
 * ```kotlin
 * val path = graphPathOf(listOf(e1, e2))
 * ```
 */
@JvmName("graphPathOfGraphEdgeList")
fun graphPathOf(edges: List<GraphEdge>): GraphPath = GraphPath(edges.map { PathStep.EdgeStep(it) })

/**
 * Returns the empty path ([GraphPath.EMPTY]).
 *
 * ```kotlin
 * val path = emptyGraphPath()
 * path.isEmpty // true
 * ```
 */
fun emptyGraphPath(): GraphPath = GraphPath.EMPTY

package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Step in a graph path, either a vertex or an edge.
 *
 * Paths alternate as `[VertexStep, EdgeStep, VertexStep, ...]`.
 *
 * ```kotlin
 * val step: PathStep = PathStep.VertexStep(vertex)
 * val edgeStep: PathStep = PathStep.EdgeStep(edge)
 * ```
 *
 */
sealed class PathStep {
    /**
     * Vertex step in a path.
     *
     * ```kotlin
     * val step = PathStep.VertexStep(GraphVertex(GraphElementId.of("v1"), "Person", mapOf("name" to "Alice")))
     * println(step.vertex.label)  // "Person"
     * ```
     *
     * @property vertex Vertex for this step.
     */
    data class VertexStep(val vertex: GraphVertex): PathStep()

    /**
     * Edge step in a path.
     *
     * ```kotlin
     * val step = PathStep.EdgeStep(edge)
     * println(step.edge.label)  // "KNOWS"
     * ```
     *
     * @property edge Edge for this step.
     */
    data class EdgeStep(val edge: GraphEdge): PathStep()
}

/**
 * Graph path.
 *
 * Consists of [PathStep] values ordered as `[VertexStep, EdgeStep, VertexStep, ...]`.
 * Represents a shortest path or all-paths traversal result between two vertices.
 *
 * @property steps Steps that make up the path.
 * @property totalWeight Total path weight. Unweighted traversal defaults to edge count (hop count).
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
         * Creates a vertex-only path with no edges.
         *
         * Use this for a single-vertex path or to represent adjacent vertices as a path.
         *
         * ```kotlin
         * val path = GraphPath.of(alice, bob, carol)
         * println(path.length)  // 3
         * ```
         *
         * @param vertices Vertices to include in the path.
         */
        fun of(vararg vertices: GraphVertex): GraphPath =
            GraphPath(vertices.map { PathStep.VertexStep(it) })
    }
}

/**
 * Creates a path from [PathStep] values.
 *
 * ```kotlin
 * val path = graphPathOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1), PathStep.VertexStep(v2))
 * ```
 */
@JvmName("graphPathOfPathSteps")
fun graphPathOf(vararg steps: PathStep): GraphPath = GraphPath(steps.toList())

/**
 * Creates a path from a [PathStep] list.
 *
 * ```kotlin
 * val path = graphPathOf(listOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1)))
 * ```
 */
@JvmName("graphPathOfPathStepsList")
fun graphPathOf(steps: List<PathStep>): GraphPath = GraphPath(steps)

/**
 * Creates a vertex-only path from vertices, with no edges.
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

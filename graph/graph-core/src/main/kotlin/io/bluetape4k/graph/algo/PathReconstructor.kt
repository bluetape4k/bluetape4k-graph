package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KotlinLogging
import io.bluetape4k.logging.warn

private val log = KotlinLogging.logger {}

/** Weighted traversal state. Depth is part of the state because a cheaper deep path
 * must not hide a more expensive shallow path that can still reach the target. */
internal data class WeightedPathState(
    val id: GraphElementId,
    val depth: Int,
)

/** Predecessor information for a depth-aware weighted traversal state. */
internal data class WeightedPathPredecessor(
    val parent: WeightedPathState,
    val edge: GraphEdge,
)

/**
 * Reconstructs a [GraphPath] by walking a predecessor map after traversal completes.
 *
 * Dijkstra/A* runners fill `cameFrom`; this function walks it backward and preserves
 * the already computed [totalWeight].
 *
 * @param targetId target vertex ID.
 * @param cameFrom vertex ID to parent vertex and connecting edge map.
 * @param vertexLookup vertex ID to [GraphVertex] lookup.
 * @param totalWeight total path weight.
 * @return reconstructed [GraphPath], or `null` when no path exists.
 */
internal fun reconstructPath(
    target: WeightedPathState,
    cameFrom: Map<WeightedPathState, WeightedPathPredecessor>,
    vertexLookup: (GraphElementId) -> GraphVertex?,
    totalWeight: Double,
): GraphPath? {
    val steps = mutableListOf<PathStep>()
    var current = target

    while (true) {
        val entry = cameFrom[current] ?: break
        val currentVertex = vertexLookup(current.id) ?: run {
            log.warn { "PathReconstructor: vertex ${current.id} not found during reconstruction (target=${target.id})" }
            return null
        }
        steps.add(PathStep.VertexStep(currentVertex))
        steps.add(PathStep.EdgeStep(entry.edge))
        current = entry.parent
    }

    // Add the source vertex.
    val startVertex = vertexLookup(current.id) ?: run {
        log.warn { "PathReconstructor: start vertex ${current.id} not found during reconstruction" }
        return null
    }
    steps.add(PathStep.VertexStep(startVertex))

    steps.reverse()
    return GraphPath(steps = steps, totalWeight = totalWeight)
}

package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KotlinLogging
import io.bluetape4k.logging.warn

private val log = KotlinLogging.logger {}

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
    targetId: GraphElementId,
    cameFrom: Map<GraphElementId, Pair<GraphVertex, GraphEdge>>,
    vertexLookup: (GraphElementId) -> GraphVertex?,
    totalWeight: Double,
): GraphPath? {
    val steps = mutableListOf<PathStep>()
    var currentId = targetId

    while (true) {
        val entry = cameFrom[currentId] ?: break
        val (parentVertex, edge) = entry
        val currentVertex = vertexLookup(currentId) ?: run {
            log.warn { "PathReconstructor: vertex $currentId not found during reconstruction (target=$targetId)" }
            return null
        }
        steps.add(PathStep.VertexStep(currentVertex))
        steps.add(PathStep.EdgeStep(edge))
        currentId = parentVertex.id
    }

    // Add the source vertex.
    val startVertex = vertexLookup(currentId) ?: run {
        log.warn { "PathReconstructor: start vertex $currentId not found during reconstruction" }
        return null
    }
    steps.add(PathStep.VertexStep(startVertex))

    steps.reverse()
    return GraphPath(steps = steps, totalWeight = totalWeight)
}

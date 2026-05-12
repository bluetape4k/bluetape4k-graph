package io.bluetape4k.graph.io.graphml

import java.io.Serializable

/**
 * GraphML import options.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
 * import io.bluetape4k.graph.io.graphml.UnsupportedGraphMlElementPolicy
 *
 * val options = GraphMlImportOptions(
 *     labelAttrName = "kind",
 *     unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL,
 *     defaultVertexLabel = "Entity",
 *     defaultEdgeLabel = "RELATED_TO",
 * )
 * ```
 *
 * @param labelAttrName GraphML `attr.name` value used as the vertex or edge label.
 * @param unsupportedElementPolicy Policy for unsupported GraphML elements.
 * @param defaultVertexLabel Label used when a vertex does not contain label data.
 * @param defaultEdgeLabel Label used when an edge does not contain label data.
 */
data class GraphMlImportOptions(
    val labelAttrName: String = "label",
    val unsupportedElementPolicy: UnsupportedGraphMlElementPolicy = UnsupportedGraphMlElementPolicy.SKIP,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "EDGE",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

package io.bluetape4k.graph.io.graphml

import java.io.Serializable

/**
 * GraphML export options.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlEdgeDefault
 * import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
 *
 * val options = GraphMlExportOptions(
 *     labelAttrName = "kind",
 *     edgeDefault = GraphMlEdgeDefault.DIRECTED,
 *     graphId = "catalog",
 * )
 * ```
 *
 * @param labelAttrName `attr.name` value used to store vertex and edge labels.
 * @param edgeDefault GraphML `<graph edgedefault>` value.
 * @param graphId GraphML `<graph id>` value.
 * @param encoding XML declaration encoding.
 */
data class GraphMlExportOptions(
    val labelAttrName: String = "label",
    val edgeDefault: GraphMlEdgeDefault = GraphMlEdgeDefault.DIRECTED,
    val graphId: String = "G",
    val encoding: String = "UTF-8",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

package io.bluetape4k.graph.io.graphml

/** GraphML `<graph edgedefault="...">` 값. */
/**
 * GraphML edge direction default for a `<graph>` element.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlEdgeDefault
 * import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
 *
 * val options = GraphMlExportOptions(edgeDefault = GraphMlEdgeDefault.DIRECTED)
 * ```
 */
enum class GraphMlEdgeDefault(val xmlName: String) {
    DIRECTED("directed"),
    UNDIRECTED("undirected");

    companion object {
        fun fromXml(name: String): GraphMlEdgeDefault =
            entries.firstOrNull { it.xmlName == name } ?: DIRECTED
    }
}

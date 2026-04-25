package io.bluetape4k.graph.io.graphml

/** GraphML `<graph edgedefault="...">` 값. */
enum class GraphMlEdgeDefault(val xmlName: String) {
    DIRECTED("directed"),
    UNDIRECTED("undirected");

    companion object {
        fun fromXml(name: String): GraphMlEdgeDefault =
            entries.firstOrNull { it.xmlName == name } ?: DIRECTED
    }
}

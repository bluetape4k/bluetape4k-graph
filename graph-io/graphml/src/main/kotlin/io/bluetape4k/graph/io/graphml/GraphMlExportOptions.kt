package io.bluetape4k.graph.io.graphml

import java.io.Serializable

/**
 * GraphML 익스포트 옵션.
 *
 * @param labelAttrName 정점/간선 라벨을 저장할 `attr.name` 값
 * @param edgeDefault GraphML `<graph edgedefault>` 값
 * @param graphId `<graph id>` 값
 * @param encoding XML 선언의 인코딩
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

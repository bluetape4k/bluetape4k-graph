package io.bluetape4k.graph.io.graphml

import java.io.Serializable

/**
 * GraphML 임포트 옵션.
 *
 * @param labelAttrName 정점/간선의 라벨로 사용할 GraphML `attr.name` 값
 * @param unsupportedElementPolicy 미지원 요소 처리 정책
 * @param defaultVertexLabel 라벨 데이터가 없는 정점에 사용할 기본 라벨
 * @param defaultEdgeLabel 라벨 데이터가 없는 간선에 사용할 기본 라벨
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

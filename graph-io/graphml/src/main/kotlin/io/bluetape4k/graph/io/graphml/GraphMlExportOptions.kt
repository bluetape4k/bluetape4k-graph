package io.bluetape4k.graph.io.graphml

import java.io.Serializable

/**
 * GraphML export options.
 *
 * 예제:
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
 * Exporter는 동일한 vertex/edge property key에서 관찰한 non-null JVM 타입을
 * GraphML `attr.type`으로 기록한다. 지원 타입은 [Int], [Long], [Float], [Double],
 * [Boolean], [String]이며, 그 밖의 단일 타입은 `string`으로 기록한다. 동일 key에
 * 서로 다른 non-null 타입이 섞이면 모호한 변환을 피하기 위해 즉시 실패하고,
 * null 값은 타입 추론에서 제외하며, `<data>` 출력 여부는
 * [io.bluetape4k.graph.io.options.GraphExportOptions.includeEmptyProperties]를 따른다.
 *
 * @param labelAttrName vertex와 edge label을 저장할 `attr.name` 값
 * @param edgeDefault GraphML `<graph edgedefault>` 값
 * @param graphId GraphML `<graph id>` 값
 * @param encoding XML 선언 encoding
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

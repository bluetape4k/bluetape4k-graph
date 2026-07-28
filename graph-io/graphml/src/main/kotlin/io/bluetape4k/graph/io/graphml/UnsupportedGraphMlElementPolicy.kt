package io.bluetape4k.graph.io.graphml

/** GraphML 파싱 중 알 수 없는 요소를 만났을 때의 정책. */
/**
 * Policy for GraphML elements not supported by the importer.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
 * import io.bluetape4k.graph.io.graphml.UnsupportedGraphMlElementPolicy
 *
 * val strictOptions = GraphMlImportOptions(
 *     unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL,
 * )
 * ```
 */
enum class UnsupportedGraphMlElementPolicy { SKIP, FAIL }

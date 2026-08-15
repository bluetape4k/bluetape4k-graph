package io.bluetape4k.graph.io.options

import io.bluetape4k.graph.repository.DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * graph export operation option.
 *
 * 빈 label 집합은 전체 label export를 요청한다. backend가
 * [io.bluetape4k.graph.repository.GraphLabelDiscovery]를 제공하지 않으면
 * exporter가 빈 결과를 성공으로 반환하지 않고 명확히 실패한다.
 * [vertexLabels] 또는 [edgeLabels]의 blank value는 fail fast 처리한다.
 *
 * @property exportChunkSize streaming-capable exporter가 repository chunk당 요청하는 최대
 * record 수. CSV처럼 global header가 필요한 format은
 * 여전히 format-specific pre-scan을 수행할 수 있다.
 */
data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),
    val edgeLabels: Set<String> = emptySet(),
    val includeEmptyProperties: Boolean = true,
    val exportChunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
) : Serializable {
    init {
        vertexLabels.forEach { it.requireNotBlank("vertexLabels element") }
        edgeLabels.forEach { it.requireNotBlank("edgeLabels element") }
        exportChunkSize.requirePositiveNumber("exportChunkSize")
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

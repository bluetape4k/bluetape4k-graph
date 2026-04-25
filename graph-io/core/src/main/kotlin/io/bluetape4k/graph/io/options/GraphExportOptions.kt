package io.bluetape4k.graph.io.options

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * 그래프 익스포트 옵션.
 * `vertexLabels`/`edgeLabels`에 빈 문자열이 포함되면 즉시 실패한다.
 */
data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),
    val edgeLabels: Set<String> = emptySet(),
    val includeEmptyProperties: Boolean = true,
) : Serializable {
    init {
        vertexLabels.forEach { it.requireNotBlank("vertexLabels element") }
        edgeLabels.forEach { it.requireNotBlank("edgeLabels element") }
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

package io.bluetape4k.graph.io.options

import io.bluetape4k.graph.repository.DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * Options for graph export operations.
 *
 * Blank values in [vertexLabels] or [edgeLabels] fail fast.
 *
 * @property exportChunkSize streaming-capable exporters request at most this
 * many records per repository chunk. Formats that need a global header, such as
 * CSV, may still perform a format-specific pre-scan.
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

package io.bluetape4k.graph.io.report

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/** 임포트/익스포트 중 발생한 개별 실패 정보 */
data class GraphIoFailure(
    val phase: GraphIoPhase,
    val severity: GraphIoFailureSeverity = GraphIoFailureSeverity.ERROR,
    val location: String? = null,
    val sourceName: String? = null,
    val fileRole: GraphIoFileRole? = null,
    val recordId: String? = null,
    val columnName: String? = null,
    val elementName: String? = null,
    val message: String,
) : Serializable {
    init {
        message.requireNotBlank("message")
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

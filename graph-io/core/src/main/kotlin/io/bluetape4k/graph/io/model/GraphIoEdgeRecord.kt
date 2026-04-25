package io.bluetape4k.graph.io.model

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/** 그래프 IO 작업용 간선 레코드. 양 끝점의 외부 ID, 레이블, 속성을 보유한다. */
data class GraphIoEdgeRecord(
    val externalId: String? = null,
    val label: String,
    val fromExternalId: String,
    val toExternalId: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    init {
        label.requireNotBlank("label")
        fromExternalId.requireNotBlank("fromExternalId")
        toExternalId.requireNotBlank("toExternalId")
        externalId?.requireNotBlank("externalId")
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

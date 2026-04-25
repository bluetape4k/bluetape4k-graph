package io.bluetape4k.graph.io.model

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/** 그래프 IO 작업용 정점 레코드. 외부 ID, 레이블, 속성을 보유한다. */
data class GraphIoVertexRecord(
    val externalId: String,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    init {
        externalId.requireNotBlank("externalId")
        label.requireNotBlank("label")
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

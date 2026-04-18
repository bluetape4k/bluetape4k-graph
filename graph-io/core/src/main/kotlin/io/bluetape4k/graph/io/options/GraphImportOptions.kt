package io.bluetape4k.graph.io.options

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * 그래프 임포트 옵션.
 * `batchSize`는 진행 보고/플러시 주기이며, `maxEdgeBufferSize`는 NDJSON 엣지 버퍼의 상한이다.
 * `preserveExternalIdProperty`가 null이면 외부 ID를 정점 속성으로 보존하지 않는다.
 */
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
) : Serializable {
    init {
        batchSize.requirePositiveNumber("batchSize")
        maxEdgeBufferSize.requirePositiveNumber("maxEdgeBufferSize")
        defaultVertexLabel.requireNotBlank("defaultVertexLabel")
        defaultEdgeLabel.requireNotBlank("defaultEdgeLabel")
        preserveExternalIdProperty?.requireNotBlank("preserveExternalIdProperty")
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

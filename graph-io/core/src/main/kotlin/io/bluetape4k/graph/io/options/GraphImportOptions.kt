package io.bluetape4k.graph.io.options

import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointStore
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * 그래프 임포트 옵션.
 * `batchSize`는 임포터가 백엔드 `createVertices`/`createEdges` 호출을 플러시하는 쓰기 배치 크기이며,
 * `maxEdgeBufferSize`는 NDJSON 엣지 버퍼의 상한이다.
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
    /** Optional durable checkpoint store; null preserves the existing one-attempt behavior. */
    val checkpointStore: GraphImportCheckpointStore? = null,
    /** Opaque key used by [checkpointStore]; raw paths and payloads must not be used. */
    val checkpointKey: String? = null,
    /** Resume only when an existing compatible checkpoint is found. */
    val resumeFromCheckpoint: Boolean = false,
) : Serializable {
    init {
        batchSize.requirePositiveNumber("batchSize")
        maxEdgeBufferSize.requirePositiveNumber("maxEdgeBufferSize")
        defaultVertexLabel.requireNotBlank("defaultVertexLabel")
        defaultEdgeLabel.requireNotBlank("defaultEdgeLabel")
        preserveExternalIdProperty?.requireNotBlank("preserveExternalIdProperty")
        checkpointKey?.requireNotBlank("checkpointKey")
        require(!resumeFromCheckpoint || (checkpointStore != null && checkpointKey != null)) {
            "resumeFromCheckpoint requires checkpointStore and checkpointKey"
        }
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

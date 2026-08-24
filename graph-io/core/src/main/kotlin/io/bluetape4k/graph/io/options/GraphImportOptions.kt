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
 * checkpoint 옵션을 지정하면 포맷 importer가 플러시된 경계와 외부 ID 매핑을 저장하며,
 * stream 소스는 재시도 사이에 유지되는 `checkpointSourceIdentity`를 명시해야 한다.
 */
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
    /** 영속 checkpoint 저장소. null이면 기존 단일 시도 동작을 유지한다. */
    val checkpointStore: GraphImportCheckpointStore? = null,
    /** [checkpointStore]에서 사용하는 불투명 키. 원본 경로와 payload를 직접 넣지 않는다. */
    val checkpointKey: String? = null,
    /** 호환되는 기존 checkpoint가 있을 때만 재개한다. */
    val resumeFromCheckpoint: Boolean = false,
) : Serializable {
    init {
        batchSize.requirePositiveNumber("batchSize")
        maxEdgeBufferSize.requirePositiveNumber("maxEdgeBufferSize")
        defaultVertexLabel.requireNotBlank("defaultVertexLabel")
        defaultEdgeLabel.requireNotBlank("defaultEdgeLabel")
        preserveExternalIdProperty?.requireNotBlank("preserveExternalIdProperty")
        checkpointKey?.requireNotBlank("checkpointKey")
        require(checkpointStore == null || checkpointKey != null) {
            "checkpointStore requires checkpointKey"
        }
        require(!resumeFromCheckpoint || (checkpointStore != null && checkpointKey != null)) {
            "resumeFromCheckpoint requires checkpointStore and checkpointKey"
        }
    }

    /**
     * 스트림 재개용 source identity를 지정하는 확장 생성자.
     * 기존 data-class primary constructor와 default/copy ABI는 그대로 유지한다.
     */
    constructor(
        checkpointSourceIdentity: String?,
        batchSize: Int = 1_000,
        maxEdgeBufferSize: Int = 100_000,
        onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
        onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
        defaultVertexLabel: String = "Vertex",
        defaultEdgeLabel: String = "Edge",
        preserveExternalIdProperty: String? = "_graphIoExternalId",
        checkpointStore: GraphImportCheckpointStore? = null,
        checkpointKey: String? = null,
        resumeFromCheckpoint: Boolean = false,
    ) : this(
        batchSize = batchSize,
        maxEdgeBufferSize = maxEdgeBufferSize,
        onDuplicateVertexId = onDuplicateVertexId,
        onMissingEdgeEndpoint = onMissingEdgeEndpoint,
        defaultVertexLabel = defaultVertexLabel,
        defaultEdgeLabel = defaultEdgeLabel,
        preserveExternalIdProperty = preserveExternalIdProperty,
        checkpointStore = checkpointStore,
        checkpointKey = checkpointKey,
        resumeFromCheckpoint = resumeFromCheckpoint,
    ) {
        setCheckpointSourceIdentity(checkpointSourceIdentity)
    }

    private var checkpointSourceIdentityValue: String? = null

    /** 스트림 재개 사이에 유지할 선택적 source identity/version. */
    val checkpointSourceIdentity: String?
        get() = checkpointSourceIdentityValue

    private fun setCheckpointSourceIdentity(value: String?) {
        value?.requireNotBlank("checkpointSourceIdentity")
        require(value == null || value.length <= MAX_CHECKPOINT_SOURCE_IDENTITY_LENGTH) {
            "checkpointSourceIdentity must be at most 4096 characters"
        }
        checkpointSourceIdentityValue = value
    }

    /** Checkpoint mode uses single-record writes so a backend batch cannot hide a partial prefix. */
    val writeBatchSize: Int
        get() = if (checkpointStore == null) batchSize else 1

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
        private const val MAX_CHECKPOINT_SOURCE_IDENTITY_LENGTH: Int = 4_096
    }
}

/**
 * data-class [copy]가 body property인 source identity를 잃지 않도록 checkpoint 옵션을 복제한다.
 * 일반 import 옵션만 복제할 때는 기존 [GraphImportOptions.copy]를 사용해도 된다.
 */
fun GraphImportOptions.copyWithCheckpointSourceIdentity(
    batchSize: Int = this.batchSize,
    maxEdgeBufferSize: Int = this.maxEdgeBufferSize,
    onDuplicateVertexId: DuplicateVertexPolicy = this.onDuplicateVertexId,
    onMissingEdgeEndpoint: MissingEndpointPolicy = this.onMissingEdgeEndpoint,
    defaultVertexLabel: String = this.defaultVertexLabel,
    defaultEdgeLabel: String = this.defaultEdgeLabel,
    preserveExternalIdProperty: String? = this.preserveExternalIdProperty,
    checkpointStore: GraphImportCheckpointStore? = this.checkpointStore,
    checkpointKey: String? = this.checkpointKey,
    resumeFromCheckpoint: Boolean = this.resumeFromCheckpoint,
    checkpointSourceIdentity: String? = this.checkpointSourceIdentity,
): GraphImportOptions = GraphImportOptions(
    checkpointSourceIdentity = checkpointSourceIdentity,
    batchSize = batchSize,
    maxEdgeBufferSize = maxEdgeBufferSize,
    onDuplicateVertexId = onDuplicateVertexId,
    onMissingEdgeEndpoint = onMissingEdgeEndpoint,
    defaultVertexLabel = defaultVertexLabel,
    defaultEdgeLabel = defaultEdgeLabel,
    preserveExternalIdProperty = preserveExternalIdProperty,
    checkpointStore = checkpointStore,
    checkpointKey = checkpointKey,
    resumeFromCheckpoint = resumeFromCheckpoint,
)

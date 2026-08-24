package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import java.util.UUID

/**
 * 하나의 importer 시도에 대한 checkpoint lifecycle을 조정한다.
 *
 * checkpoint는 importer가 batch writer를 flush한 뒤에만 저장하므로, 저장되는 외부 ID 맵은
 * 임시 ID가 아니라 backend ID를 포함한다.
 */
@Suppress("TooGenericExceptionCaught")
class GraphImportCheckpointSession(
    private val format: GraphIoFormat,
    private val sourceIdentity: String,
    private val options: GraphImportOptions,
    private val idMap: GraphIoExternalIdMap,
    private val importOptionsIdentity: String = GraphImportCheckpointIdentity.optionsIdentity(options),
) {
    private val store = options.checkpointStore
    private val key = options.checkpointKey
    private val attemptId = UUID.randomUUID().toString()
    private var claimAcquired = false
    private var committedVertices = 0L
    private var committedEdges = 0L
    private var lastBoundary = "VERTICES"

    val resumeVerticesProcessed: Long
    val resumeEdgesProcessed: Long

    init {
        if (store == null) {
            resumeVerticesProcessed = 0L
            resumeEdgesProcessed = 0L
        } else {
            val checkpointKey = requireNotNull(key)
            val checkpoint = store.load(checkpointKey)
            if (!options.resumeFromCheckpoint && checkpoint != null) {
                throw GraphImportCheckpointConflictException(
                    "checkpoint already exists for key '$key'; resume it or choose a new key",
                )
            }
            if (!store.claim(checkpointKey, attemptId)) {
                throw GraphImportCheckpointConflictException("checkpoint key '$key' is already claimed")
            }
            claimAcquired = true
            try {
                if (options.resumeFromCheckpoint) {
                    val existing = checkpoint ?: throw GraphImportCheckpointConflictException(
                        "no checkpoint exists for key '$key'",
                    )
                    GraphImportCheckpointValidator.requireCompatible(
                        existing,
                        format,
                        sourceIdentity,
                        importOptionsIdentity,
                    )
                    requireResumable(existing)
                    try {
                        idMap.restore(existing.externalIdMappingState)
                    } catch (error: IllegalArgumentException) {
                        throw GraphImportCheckpointConflictException(
                            "checkpoint external ID mapping is invalid: ${error.message}",
                            error,
                        )
                    }
                    if (existing.verticesProcessed > 0 && idMap.size == 0) {
                        throw GraphImportCheckpointConflictException(
                            "checkpoint external ID mapping is empty for committed vertices",
                        )
                    }
                    store.save(checkpointKey, existing.withMetadata(existing.importOptionsIdentity, attemptId))
                    resumeVerticesProcessed = existing.verticesProcessed
                    resumeEdgesProcessed = existing.edgesProcessed
                    committedVertices = existing.verticesProcessed
                    committedEdges = existing.edgesProcessed
                } else {
                    store.save(checkpointKey, checkpoint(format, GraphImportCheckpointPhase.DISCOVERED, 0L, 0L, null))
                    resumeVerticesProcessed = 0L
                    resumeEdgesProcessed = 0L
                }
            } catch (error: Throwable) {
                store.release(checkpointKey, attemptId)
                claimAcquired = false
                throw error
            }
        }
    }

    fun shouldSkipVertex(readCount: Long): Boolean = readCount <= resumeVerticesProcessed

    fun shouldSkipEdge(readCount: Long): Boolean = readCount <= resumeEdgesProcessed

    /** 대기 중인 정점 batch를 모두 flush한 뒤 안전한 정점 경계를 저장한다. */
    fun verticesCommitted(verticesProcessed: Long) {
        lastBoundary = "VERTICES"
        committedVertices = verticesProcessed
        if (resumeEdgesProcessed == 0L) {
            committedEdges = 0L
        }
        save(
            phase = if (committedEdges > 0) GraphImportCheckpointPhase.EDGES else GraphImportCheckpointPhase.VERTICES,
            verticesProcessed = verticesProcessed,
            edgesProcessed = committedEdges,
            failureBoundary = null,
        )
    }

    /** 대기 중인 간선 batch를 모두 flush한 뒤 안전한 간선 경계를 저장한다. */
    fun edgesCommitted(verticesProcessed: Long, edgesProcessed: Long) {
        lastBoundary = "EDGES"
        committedVertices = verticesProcessed
        committedEdges = edgesProcessed
        save(GraphImportCheckpointPhase.EDGES, verticesProcessed, edgesProcessed, null)
    }

    /** 성공 또는 부분 import가 정상 반환된 뒤 checkpoint를 삭제한다. */
    fun completed() {
        if (store == null || !claimAcquired) return
        val checkpointKey = requireNotNull(key)
        try {
            val current = store.load(checkpointKey)
            if (current?.attemptId != attemptId) {
                throw GraphImportCheckpointConflictException("checkpoint ownership was lost for key '$key'")
            }
            store.delete(checkpointKey)
        } finally {
            store.release(checkpointKey, attemptId)
            claimAcquired = false
        }
    }

    /** 다음 시도가 재개할 수 있도록 마지막 안전 경계를 보존한다. */
    fun failed(
        failureBoundary: String,
        message: String? = null,
    ) {
        if (store == null || !claimAcquired) return
        lastBoundary = failureBoundary
        val checkpointKey = requireNotNull(key)
        try {
            val current = store.load(checkpointKey)
            if (current?.attemptId != attemptId) return
            save(
                phase = GraphImportCheckpointPhase.FAILED,
                verticesProcessed = committedVertices,
                edgesProcessed = committedEdges,
                failureBoundary = failureBoundary,
                message = message,
            )
        } finally {
            store.release(checkpointKey, attemptId)
            claimAcquired = false
        }
    }

    private fun save(
        phase: GraphImportCheckpointPhase,
        verticesProcessed: Long,
        edgesProcessed: Long,
        failureBoundary: String?,
        message: String? = null,
    ) {
        if (store == null) return
        val checkpointKey = requireNotNull(key)
        check(claimAcquired) { "checkpoint key '$checkpointKey' is not claimed by this import attempt" }
        val current = store.load(checkpointKey)
        if (current?.attemptId != attemptId) {
            throw GraphImportCheckpointConflictException("checkpoint ownership was lost for key '$checkpointKey'")
        }
        store.save(
            checkpointKey,
            checkpoint(format, phase, verticesProcessed, edgesProcessed, failureBoundary, message),
        )
    }

    private fun checkpoint(
        format: GraphIoFormat,
        phase: GraphImportCheckpointPhase,
        verticesProcessed: Long,
        edgesProcessed: Long,
        failureBoundary: String?,
        message: String? = null,
    ) = GraphImportCheckpoint(
        importOptionsIdentity = importOptionsIdentity,
        attemptId = attemptId,
        format = format,
        sourceIdentity = sourceIdentity,
        phase = phase,
        verticesProcessed = verticesProcessed,
        edgesProcessed = edgesProcessed,
        externalIdMappingState = idMap.snapshot(),
        failureBoundary = listOfNotNull(failureBoundary, message).joinToString(":").ifBlank { null },
    )

    /** 예외·취소가 importer 경계를 빠져나가도 claim을 보존하고 해제한다. */
    fun close() {
        val checkpointStore = store ?: return
        if (!claimAcquired) return
        val checkpointKey = requireNotNull(key)
        try {
            val current = checkpointStore.load(checkpointKey)
            if (current?.attemptId == attemptId) {
                runCatching {
                    checkpointStore.save(
                        checkpointKey,
                        checkpoint(
                            format = format,
                            phase = GraphImportCheckpointPhase.FAILED,
                            verticesProcessed = committedVertices,
                            edgesProcessed = committedEdges,
                            failureBoundary = lastBoundary,
                            message = "unhandled importer failure",
                        ),
                    )
                }
            }
        } finally {
            checkpointStore.release(checkpointKey, attemptId)
            claimAcquired = false
        }
    }

    @Suppress("ThrowsCount")
    private fun requireResumable(checkpoint: GraphImportCheckpoint) {
        if (checkpoint.phase == GraphImportCheckpointPhase.COMPLETED) {
            throw GraphImportCheckpointConflictException("completed checkpoint cannot be resumed")
        }
        if (checkpoint.verticesProcessed > 0 && checkpoint.externalIdMappingState.isNullOrBlank()) {
            throw GraphImportCheckpointConflictException("checkpoint is missing external ID mapping state")
        }
        if (checkpoint.phase == GraphImportCheckpointPhase.FAILED &&
            checkpoint.failureBoundary?.substringBefore(':') !in setOf("VERTICES", "EDGES")
        ) {
            throw GraphImportCheckpointConflictException("failed checkpoint has no resumable boundary")
        }
    }
}

package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.graph.io.report.GraphIoFormat
import java.io.Serializable

/** Import checkpoint phase shared by every graph-io format. */
enum class GraphImportCheckpointPhase { DISCOVERED, VERTICES, EDGES, COMPLETED, FAILED }

/**
 * Durable, backend-neutral import position.
 *
 * [sourceIdentity] and [externalIdMappingState] are opaque caller-owned values;
 * implementations must not put raw source paths, payloads, or credentials in them.
 */
data class GraphImportCheckpoint(
    val format: GraphIoFormat,
    val sourceIdentity: String,
    val phase: GraphImportCheckpointPhase,
    val verticesProcessed: Long,
    val edgesProcessed: Long,
    val externalIdMappingState: String? = null,
    val failureBoundary: String? = null,
    val version: Int = CURRENT_VERSION,
) : Serializable {
    init {
        require(sourceIdentity.isNotBlank()) { "sourceIdentity must not be blank" }
        require(verticesProcessed >= 0) { "verticesProcessed must be >= 0" }
        require(edgesProcessed >= 0) { "edgesProcessed must be >= 0" }
        require(version > 0) { "version must be positive" }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
        const val CURRENT_VERSION: Int = 1
    }
}

/** Durable storage abstraction for opt-in checkpoint/resume. */
interface GraphImportCheckpointStore {
    fun load(key: String): GraphImportCheckpoint?

    fun save(key: String, checkpoint: GraphImportCheckpoint)

    fun delete(key: String)
}

/** Small process-local store useful for tests and single-process callers. */
class InMemoryGraphImportCheckpointStore : GraphImportCheckpointStore {
    private val checkpoints = mutableMapOf<String, GraphImportCheckpoint>()

    @Synchronized
    override fun load(key: String): GraphImportCheckpoint? = checkpoints[key]

    @Synchronized
    override fun save(key: String, checkpoint: GraphImportCheckpoint) {
        checkpoints[key] = checkpoint
    }

    @Synchronized
    override fun delete(key: String) {
        checkpoints.remove(key)
    }
}

class GraphImportCheckpointConflictException(message: String) : IllegalStateException(message)

/**
 * Validates a stored checkpoint before a resume starts and rejects a changed source
 * or an unsupported checkpoint version instead of silently duplicating writes.
 */
object GraphImportCheckpointValidator {
    fun requireCompatible(
        checkpoint: GraphImportCheckpoint,
        format: GraphIoFormat,
        sourceIdentity: String,
    ) {
        if (checkpoint.format != format || checkpoint.sourceIdentity != sourceIdentity) {
            throw GraphImportCheckpointConflictException("checkpoint does not match the requested import")
        }
        if (checkpoint.version != GraphImportCheckpoint.CURRENT_VERSION) {
            throw GraphImportCheckpointConflictException("checkpoint version is not supported")
        }
    }
}

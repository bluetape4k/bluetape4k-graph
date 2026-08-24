package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.graph.io.report.GraphIoFormat
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/** 모든 graph-io 포맷이 공유하는 import checkpoint 단계. */
enum class GraphImportCheckpointPhase { DISCOVERED, VERTICES, EDGES, COMPLETED, FAILED }

/**
 * backend와 무관하게 영속화할 수 있는 import 위치.
 *
 * [sourceIdentity]와 [externalIdMappingState]는 호출자가 관리하는 불투명 값이다.
 * 원본 경로, payload, credential을 그대로 저장해서는 안 된다.
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

    /**
     * 새 checkpoint metadata를 부착하는 확장 생성자.
     * 기존 data-class primary constructor와 default/copy ABI는 유지한다.
     */
    constructor(
        importOptionsIdentity: String?,
        attemptId: String?,
        format: GraphIoFormat,
        sourceIdentity: String,
        phase: GraphImportCheckpointPhase,
        verticesProcessed: Long,
        edgesProcessed: Long,
        externalIdMappingState: String? = null,
        failureBoundary: String? = null,
        version: Int = CURRENT_VERSION,
    ) : this(
        format = format,
        sourceIdentity = sourceIdentity,
        phase = phase,
        verticesProcessed = verticesProcessed,
        edgesProcessed = edgesProcessed,
        externalIdMappingState = externalIdMappingState,
        failureBoundary = failureBoundary,
        version = version,
    ) {
        this.importOptionsIdentityValue = importOptionsIdentity
        this.attemptIdValue = attemptId
    }

    private var importOptionsIdentityValue: String? = null
    private var attemptIdValue: String? = null

    /** import 의미의 지문. 정책이 바뀐 재개를 조용히 허용하지 않는다. */
    val importOptionsIdentity: String?
        get() = importOptionsIdentityValue

    /** 동일 프로세스의 같은 키 import를 fencing하는 시도 소유자. */
    val attemptId: String?
        get() = attemptIdValue

    fun withMetadata(importOptionsIdentity: String?, attemptId: String?): GraphImportCheckpoint =
        copy().also {
            it.importOptionsIdentityValue = importOptionsIdentity
            it.attemptIdValue = attemptId
        }

    companion object {
        private const val serialVersionUID: Long = 1L
        const val CURRENT_VERSION: Int = 2
    }
}

/** 선택적으로 활성화하는 checkpoint/resume의 영속 저장소 추상화. */
interface GraphImportCheckpointStore {
    fun load(key: String): GraphImportCheckpoint?

    fun save(key: String, checkpoint: GraphImportCheckpoint)

    fun delete(key: String)

    /**
     * 하나의 importer 시도를 위해 키를 선점한다. 공유 저장소 구현은 원자적
     * compare-and-set 또는 lease 연산으로 override해야 한다.
     */
    fun claim(key: String, attemptId: String): Boolean = GraphImportCheckpointClaims.claim(this, key, attemptId)

    /**
     * 프로세스 로컬 선점을 해제한다. 공유 저장소는 claim/release뿐 아니라 save/delete도
     * 현재 checkpoint의 attempt id와 일치할 때만 반영하는 원자적 fencing을 제공해야 한다.
     */
    fun release(key: String, attemptId: String) = GraphImportCheckpointClaims.release(this, key, attemptId)
}

/** 테스트와 단일 프로세스 호출자에 유용한 작은 프로세스 로컬 저장소. */
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

class GraphImportCheckpointConflictException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private object GraphImportCheckpointClaims {
    private data class ClaimKey(val store: GraphImportCheckpointStore, val key: String)
    private data class Claim(val attemptId: String, val owner: Thread)

    private val claims = ConcurrentHashMap<ClaimKey, Claim>()

    @Suppress("ReturnCount")
    fun claim(store: GraphImportCheckpointStore, key: String, attemptId: String): Boolean {
        val claimKey = ClaimKey(store, key)
        val next = Claim(attemptId, Thread.currentThread())
        while (true) {
            val current = claims[claimKey]
            if (current == null) {
                if (claims.putIfAbsent(claimKey, next) == null) return true
            } else if (!current.owner.isAlive) {
                if (claims.replace(claimKey, current, next)) return true
            } else {
                return false
            }
        }
    }

    fun release(store: GraphImportCheckpointStore, key: String, attemptId: String) {
        val claimKey = ClaimKey(store, key)
        claims.computeIfPresent(claimKey) { _, claim ->
            if (claim.attemptId == attemptId) null else claim
        }
    }
}

/**
 * 재개 전에 저장된 checkpoint를 검증하고, 변경된 source나 지원하지 않는 checkpoint
 * 버전은 중복 쓰기를 유발하지 않도록 즉시 거부한다.
 */
object GraphImportCheckpointValidator {
    @Suppress("ThrowsCount")
    fun requireCompatible(
        checkpoint: GraphImportCheckpoint,
        format: GraphIoFormat,
        sourceIdentity: String,
        importOptionsIdentity: String? = null,
    ) {
        if (checkpoint.format != format || checkpoint.sourceIdentity != sourceIdentity) {
            throw GraphImportCheckpointConflictException("checkpoint does not match the requested import")
        }
        if (checkpoint.version != GraphImportCheckpoint.CURRENT_VERSION) {
            throw GraphImportCheckpointConflictException("checkpoint version is not supported")
        }
        if (importOptionsIdentity != null && checkpoint.importOptionsIdentity != importOptionsIdentity) {
            throw GraphImportCheckpointConflictException("checkpoint import options do not match the requested import")
        }
    }
}

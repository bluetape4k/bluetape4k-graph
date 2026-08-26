package io.bluetape4k.graph.io.workflow

import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpoint
import io.bluetape4k.graph.io.report.GraphIoFormat
import java.io.Serializable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

enum class GraphImportWorkflowState {
    DISCOVERED,
    VALIDATED,
    VERTICES_LOADED,
    EDGES_LOADED,
    VERIFIED,
    COMPLETED,
    FAILED,
}

enum class GraphImportSourceRole { VERTICES, EDGES, SCHEMA }

data class GraphImportSourceSpec(
    val id: String,
    val role: GraphImportSourceRole,
    val format: GraphIoFormat,
    val sourceIdentity: String,
    val label: String? = null,
    val compression: String? = null,
    val encrypted: Boolean = false,
    val dependsOn: Set<String> = emptySet(),
) : Serializable {
    init {
        require(id.isNotBlank()) { "source id must not be blank" }
        require(sourceIdentity.isNotBlank()) { "sourceIdentity must not be blank" }
        require(dependsOn.none { it == id }) { "source cannot depend on itself" }
    }

    companion object { private const val serialVersionUID: Long = 1L }
}

data class GraphImportManifest(
    val jobId: String,
    val sources: List<GraphImportSourceSpec>,
) : Serializable {
    init {
        require(jobId.isNotBlank()) { "jobId must not be blank" }
        require(sources.isNotEmpty()) { "at least one source is required" }
        val ids = sources.map { it.id }
        require(ids.size == ids.toSet().size) { "source ids must be unique" }
        val knownIds = ids.toSet()
        require(sources.all { it.dependsOn.all(knownIds::contains) }) {
            "source dependency refers to an unknown source"
        }
        require(sources.any { it.role == GraphImportSourceRole.VERTICES }) {
            "at least one vertex source is required"
        }
    }

    companion object { private const val serialVersionUID: Long = 1L }
}

data class GraphImportSourceReport(
    val sourceId: String,
    val recordsRead: Long = 0,
    val recordsSkipped: Long = 0,
    val failure: String? = null,
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

data class GraphImportWorkflowReport(
    val jobId: String,
    val state: GraphImportWorkflowState,
    val sources: List<GraphImportSourceReport> = emptyList(),
    val elapsed: Duration = Duration.ZERO,
    val checkpoint: GraphImportCheckpoint? = null,
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

interface GraphImportJobStateStore {
    fun load(jobId: String): GraphImportWorkflowReport?

    fun save(report: GraphImportWorkflowReport)

    /**
     * 하나의 job report를 load·transform·검증·save하는 원자 경계입니다.
     *
     * 기본 구현은 동일 store 인스턴스의 JVM monitor 안에서 실행됩니다. durable
     * 구현은 native transaction 또는 CAS로 이 메서드를 override해야 하며 다음
     * 계약을 유지해야 합니다.
     *
     * - `transform`은 최신 report를 입력으로 받아도 순수하고 재시도에 안전해야 합니다.
     * - 결과 `jobId`는 요청한 `jobId`와 같아야 하며, 다르면 저장하지 않고 실패해야 합니다.
     * - transform 또는 invariant 검증이 실패하면 기존 report를 변경하지 않아야 합니다.
     */
    fun update(
        jobId: String,
        transform: (GraphImportWorkflowReport?) -> GraphImportWorkflowReport,
    ): GraphImportWorkflowReport = synchronized(this) {
        val updated = transform(load(jobId))
        require(updated.jobId == jobId) { "state update jobId must match the requested jobId" }
        save(updated)
        updated
    }
}

/**
 * job별로 상태 전이를 직렬화하는 JVM-local reference store입니다.
 *
 * 서로 다른 job ID는 독립 lock을 사용하고, 같은 job의 [load], [save],
 * [update]는 동일한 reentrant lock을 공유합니다. lock entry는 참조 횟수를
 * 세고 사용자가 없을 때 제거하므로 대기 중인 thread의 interrupt나 transform
 * 실패가 lock entry를 누수시키지 않습니다. 프로세스 간 원자성이 필요한
 * durable store는 [GraphImportJobStateStore.update]를 native transaction 또는
 * CAS로 구현해야 합니다.
 */
class InMemoryGraphImportJobStateStore : GraphImportJobStateStore {
    private val reports = ConcurrentHashMap<String, GraphImportWorkflowReport>()
    private val lockRegistryMonitor = Any()
    private val lockRegistry = mutableMapOf<String, JobLock>()

    override fun load(jobId: String): GraphImportWorkflowReport? =
        withJobLock(jobId) { reports[jobId] }

    override fun save(report: GraphImportWorkflowReport) {
        withJobLock(report.jobId) {
            reports[report.jobId] = report
        }
    }

    override fun update(
        jobId: String,
        transform: (GraphImportWorkflowReport?) -> GraphImportWorkflowReport,
    ): GraphImportWorkflowReport = withJobLock(jobId) {
        val updated = transform(reports[jobId])
        require(updated.jobId == jobId) { "state update jobId must match the requested jobId" }
        reports[jobId] = updated
        updated
    }

    private fun <T> withJobLock(jobId: String, action: () -> T): T {
        val jobLock = synchronized(lockRegistryMonitor) {
            lockRegistry[jobId]?.also { it.references++ }
                ?: JobLock().also {
                    it.references = 1
                    lockRegistry[jobId] = it
                }
        }
        var acquired = false
        try {
            jobLock.lock.lockInterruptibly()
            acquired = true
            return action()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } finally {
            if (acquired) {
                jobLock.lock.unlock()
            }
            synchronized(lockRegistryMonitor) {
                jobLock.references--
                if (jobLock.references == 0) {
                    lockRegistry.remove(jobId)
                }
            }
        }
    }

    private class JobLock {
        val lock = ReentrantLock()
        var references: Int = 0
    }
}

/** Manifest validation and durable state transitions for a portable multi-source job. */
class GraphImportWorkflow(
    private val manifest: GraphImportManifest,
    private val stateStore: GraphImportJobStateStore,
) {
    fun validate(): GraphImportWorkflowReport {
        val edgeSources = manifest.sources.filter { it.role == GraphImportSourceRole.EDGES }
        require(edgeSources.all { edge -> edge.dependsOn.any { dependency ->
            manifest.sources.any { it.id == dependency && it.role == GraphImportSourceRole.VERTICES }
        } || edge.dependsOn.isEmpty() }) {
            "edge sources must depend on a vertex source or declare no dependency"
        }
        return persist(GraphImportWorkflowState.VALIDATED)
    }

    fun transition(state: GraphImportWorkflowState): GraphImportWorkflowReport {
        return persist(state)
    }

    private fun persist(state: GraphImportWorkflowState): GraphImportWorkflowReport =
        stateStore.update(manifest.jobId) { currentReport ->
            val current = currentReport?.state ?: GraphImportWorkflowState.DISCOVERED
            require(ALLOWED_TRANSITIONS[current].orEmpty().contains(state)) {
                "invalid workflow transition: $current -> $state"
            }
            currentReport?.copy(state = state)
                ?: GraphImportWorkflowReport(manifest.jobId, state)
        }

    companion object {
        private val ALLOWED_TRANSITIONS = mapOf(
            GraphImportWorkflowState.DISCOVERED to setOf(
                GraphImportWorkflowState.VALIDATED,
                GraphImportWorkflowState.FAILED,
            ),
            GraphImportWorkflowState.VALIDATED to setOf(
                GraphImportWorkflowState.VERTICES_LOADED,
                GraphImportWorkflowState.FAILED,
            ),
            GraphImportWorkflowState.VERTICES_LOADED to setOf(
                GraphImportWorkflowState.EDGES_LOADED,
                GraphImportWorkflowState.FAILED,
            ),
            GraphImportWorkflowState.EDGES_LOADED to setOf(
                GraphImportWorkflowState.VERIFIED,
                GraphImportWorkflowState.FAILED,
            ),
            GraphImportWorkflowState.VERIFIED to setOf(
                GraphImportWorkflowState.COMPLETED,
                GraphImportWorkflowState.FAILED,
            ),
        )
    }
}

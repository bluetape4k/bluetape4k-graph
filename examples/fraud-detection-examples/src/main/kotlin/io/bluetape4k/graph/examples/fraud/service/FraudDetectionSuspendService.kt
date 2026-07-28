package io.bluetape4k.graph.examples.fraud.service

import io.bluetape4k.graph.examples.fraud.schema.AccountLabel
import io.bluetape4k.graph.examples.fraud.schema.TransferredToLabel
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow

/**
 * [FraudDetectionService]의 coroutine 및 Flow 버전이다.
 *
 * Underlying graph API가 result를 stream하면 read operation은 [Flow]를 반환한다.
 *
 * ```kotlin
 * val service = FraudDetectionSuspendService(ops)
 * service.initialize()
 * val cycles = service.detectCircularTransfers().toList()
 * ```
 */
class FraudDetectionSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "fraud_detection",
) {
    companion object : KLoggingChannel()

    /**
     * Backing graph가 아직 없으면 생성한다.
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Fraud detection graph '$graphName' created" }
        }
    }

    /**
     * Account vertex를 추가한다.
     */
    suspend fun addAccount(
        accountId: String,
        ownerName: String,
        riskTier: String = "standard",
    ): GraphVertex {
        accountId.requireNotBlank("accountId")
        ownerName.requireNotBlank("ownerName")
        return ops.createVertex(
            AccountLabel.label,
            mapOf(
                AccountLabel.accountId.name to accountId,
                AccountLabel.ownerName.name to ownerName,
                AccountLabel.riskTier.name to riskTier,
            )
        )
    }

    /**
     * 두 account 사이의 directed transfer를 기록한다.
     */
    suspend fun recordTransfer(
        fromAccountId: GraphElementId,
        toAccountId: GraphElementId,
        amount: Long,
        occurredAt: String = "",
    ) {
        require(amount > 0) { "amount must be > 0, was $amount" }
        ops.createEdge(
            fromAccountId,
            toAccountId,
            TransferredToLabel.label,
            mapOf(TransferredToLabel.amount.name to amount, TransferredToLabel.occurredAt.name to occurredAt)
        )
    }

    /**
     * `A -> B -> C -> A` 같은 circular transfer chain을 탐지한다.
     */
    fun detectCircularTransfers(maxDepth: Int = 5, maxCycles: Int = 20): Flow<GraphCycle> =
        ops.detectCycles(
            CycleOptions(
                vertexLabel = AccountLabel.label,
                edgeLabel = TransferredToLabel.label,
                maxDepth = maxDepth,
                maxCycles = maxCycles,
            )
        )

    /**
     * Transfer activity로 연결된 suspicious account cluster를 찾는다.
     */
    fun detectSuspiciousClusters(minSize: Int = 2): Flow<GraphComponent> =
        ops.connectedComponents(
            ComponentOptions(
                vertexLabel = AccountLabel.label,
                edgeLabel = TransferredToLabel.label,
                minSize = minSize,
            )
        )

    /**
     * Transfer graph의 centrality 기준으로 account 순위를 매긴다.
     */
    fun rankHighRiskAccounts(limit: Int = 10): Flow<PageRankScore> {
        require(limit > 0) { "limit must be > 0, was $limit" }
        return ops.pageRank(
            PageRankOptions(
                vertexLabel = AccountLabel.label,
                edgeLabel = TransferredToLabel.label,
                topK = limit,
            )
        )
    }
}

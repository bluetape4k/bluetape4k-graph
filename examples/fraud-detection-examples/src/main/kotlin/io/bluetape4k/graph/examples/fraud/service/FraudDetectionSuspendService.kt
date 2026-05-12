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
 * Coroutine and Flow version of [FraudDetectionService].
 *
 * Read operations return [Flow] when the underlying graph API streams results.
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
     * Creates the backing graph when it does not already exist.
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Fraud detection graph '$graphName' created" }
        }
    }

    /**
     * Adds an account vertex.
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
     * Records a directed transfer between two accounts.
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
     * Detects circular transfer chains such as `A -> B -> C -> A`.
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
     * Finds suspicious account clusters connected by transfer activity.
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
     * Ranks accounts by centrality in the transfer graph.
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

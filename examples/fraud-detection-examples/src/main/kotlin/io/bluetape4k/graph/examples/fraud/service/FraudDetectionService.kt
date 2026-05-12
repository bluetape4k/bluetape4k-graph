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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank

/**
 * Fraud detection graph service built on top of [GraphOperations].
 *
 * The service models accounts as vertices and money transfers as directed edges. It demonstrates cycle
 * detection for circular transfers, connected components for suspicious clusters, and PageRank for accounts
 * that receive central transfer flow.
 *
 * ```kotlin
 * val service = FraudDetectionService(ops)
 * service.initialize()
 * val alice = service.addAccount("acct-alice", "Alice")
 * val bob = service.addAccount("acct-bob", "Bob")
 * service.recordTransfer(alice.id, bob.id, amount = 100)
 * val cycles = service.detectCircularTransfers()
 * ```
 */
class FraudDetectionService(
    private val ops: GraphOperations,
    private val graphName: String = "fraud_detection",
) {
    companion object : KLogging()

    /**
     * Creates the backing graph when it does not already exist.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Fraud detection graph '$graphName' created" }
        }
    }

    /**
     * Adds an account vertex.
     */
    fun addAccount(
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
    fun recordTransfer(
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
    fun detectCircularTransfers(maxDepth: Int = 5, maxCycles: Int = 20): List<GraphCycle> =
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
    fun detectSuspiciousClusters(minSize: Int = 2): List<GraphComponent> =
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
    fun rankHighRiskAccounts(limit: Int = 10): List<PageRankScore> {
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

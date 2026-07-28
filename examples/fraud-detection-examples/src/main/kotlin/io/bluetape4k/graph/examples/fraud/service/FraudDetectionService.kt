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
 * [GraphOperations] 위에 구성한 fraud detection graph service이다.
 *
 * 이 service는 account를 vertex로, money transfer를 directed edge로 모델링한다. Circular transfer의 cycle detection,
 * suspicious cluster의 connected component, central transfer flow를 받는 account의 PageRank를 보여준다.
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
     * Backing graph가 아직 없으면 생성한다.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Fraud detection graph '$graphName' created" }
        }
    }

    /**
     * Account vertex를 추가한다.
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
     * 두 account 사이의 directed transfer를 기록한다.
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
     * `A -> B -> C -> A` 같은 circular transfer chain을 탐지한다.
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
     * Transfer activity로 연결된 suspicious account cluster를 찾는다.
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
     * Transfer graph의 centrality 기준으로 account 순위를 매긴다.
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

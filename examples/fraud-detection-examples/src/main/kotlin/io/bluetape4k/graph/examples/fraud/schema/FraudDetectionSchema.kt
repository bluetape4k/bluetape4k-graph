package io.bluetape4k.graph.examples.fraud.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Fraud detection example에서 사용하는 account vertex이다.
 *
 * ```kotlin
 * val account = ops.createVertex(AccountLabel.label, mapOf(AccountLabel.accountId.name to "acct-1"))
 * ```
 */
object AccountLabel : VertexLabel("Account") {
    val accountId = string("accountId")
    val ownerName = string("ownerName")
    val riskTier = string("riskTier")
}

/**
 * Account 사이의 money transfer edge이다.
 *
 * ```kotlin
 * ops.createEdge(source.id, target.id, TransferredToLabel.label, mapOf(TransferredToLabel.amount.name to 100))
 * ```
 */
object TransferredToLabel : EdgeLabel("TRANSFERRED_TO", AccountLabel, AccountLabel) {
    val amount = long("amount")
    val occurredAt = string("occurredAt")
}

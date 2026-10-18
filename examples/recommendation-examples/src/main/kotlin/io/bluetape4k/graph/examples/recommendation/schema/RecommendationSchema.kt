package io.bluetape4k.graph.examples.recommendation.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Recommendation example의 user vertex이다.
 */
object UserLabel: VertexLabel("User") {
    private fun readResolve(): Any = UserLabel
    val userId = string("userId")
    val displayName = string("displayName")
    val segment = string("segment")
}

/**
 * Recommendation example의 product vertex이다.
 */
object ProductLabel: VertexLabel("Product") {
    private fun readResolve(): Any = ProductLabel
    val productId = string("productId")
    val name = string("name")
    val category = string("category")
}

/**
 * User에서 product로 이어지는 purchase edge이다.
 */
object PurchasedLabel: EdgeLabel("PURCHASED", UserLabel, ProductLabel) {
    private fun readResolve(): Any = PurchasedLabel
    val quantity = integer("quantity")
    val purchasedAt = string("purchasedAt")
}

/**
 * User 사이의 social follow edge이다.
 */
object FollowsLabel: EdgeLabel("FOLLOWS", UserLabel, UserLabel) {
    private fun readResolve(): Any = FollowsLabel
}

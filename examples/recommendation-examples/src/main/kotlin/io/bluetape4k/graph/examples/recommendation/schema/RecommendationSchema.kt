package io.bluetape4k.graph.examples.recommendation.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * User vertices in the recommendation example.
 */
object UserLabel : VertexLabel("User") {
    val userId = string("userId")
    val displayName = string("displayName")
    val segment = string("segment")
}

/**
 * Product vertices in the recommendation example.
 */
object ProductLabel : VertexLabel("Product") {
    val productId = string("productId")
    val name = string("name")
    val category = string("category")
}

/**
 * Purchase edges from users to products.
 */
object PurchasedLabel : EdgeLabel("PURCHASED", UserLabel, ProductLabel) {
    val quantity = integer("quantity")
    val purchasedAt = string("purchasedAt")
}

/**
 * Social follow edges between users.
 */
object FollowsLabel : EdgeLabel("FOLLOWS", UserLabel, UserLabel)

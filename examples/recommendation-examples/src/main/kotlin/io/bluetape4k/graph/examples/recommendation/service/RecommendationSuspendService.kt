package io.bluetape4k.graph.examples.recommendation.service

import io.bluetape4k.graph.examples.recommendation.schema.FollowsLabel
import io.bluetape4k.graph.examples.recommendation.schema.ProductLabel
import io.bluetape4k.graph.examples.recommendation.schema.PurchasedLabel
import io.bluetape4k.graph.examples.recommendation.schema.UserLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

/**
 * Coroutine and Flow version of [RecommendationService].
 */
class RecommendationSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "recommendation",
) {
    companion object : KLoggingChannel()

    /**
     * Creates the backing graph when it does not already exist.
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Recommendation graph '$graphName' created" }
        }
    }

    /**
     * Adds a user vertex.
     */
    suspend fun addUser(userId: String, displayName: String, segment: String = ""): GraphVertex {
        userId.requireNotBlank("userId")
        displayName.requireNotBlank("displayName")
        return ops.createVertex(
            UserLabel.label,
            mapOf(UserLabel.userId.name to userId, UserLabel.displayName.name to displayName, UserLabel.segment.name to segment)
        )
    }

    /**
     * Adds a product vertex.
     */
    suspend fun addProduct(productId: String, name: String, category: String = ""): GraphVertex {
        productId.requireNotBlank("productId")
        name.requireNotBlank("name")
        return ops.createVertex(
            ProductLabel.label,
            mapOf(ProductLabel.productId.name to productId, ProductLabel.name.name to name, ProductLabel.category.name to category)
        )
    }

    /**
     * Records a purchase edge from a user to a product.
     */
    suspend fun recordPurchase(userId: GraphElementId, productId: GraphElementId, quantity: Int = 1, purchasedAt: String = "") {
        require(quantity > 0) { "quantity must be > 0, was $quantity" }
        ops.createEdge(
            userId,
            productId,
            PurchasedLabel.label,
            mapOf(PurchasedLabel.quantity.name to quantity, PurchasedLabel.purchasedAt.name to purchasedAt)
        )
    }

    /**
     * Creates a directed follow edge between users.
     */
    suspend fun follow(followerId: GraphElementId, targetId: GraphElementId) {
        ops.createEdge(followerId, targetId, FollowsLabel.label, emptyMap())
    }

    /**
     * Recommends products bought by users who purchased the same products as the source user.
     */
    suspend fun recommendProducts(userId: GraphElementId, limit: Int = 10): Flow<GraphVertex> {
        require(limit > 0) { "limit must be > 0, was $limit" }

        val ownedProducts = ops.neighbors(
            userId,
            NeighborOptions(edgeLabel = PurchasedLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
        ).toList()
        val ownedProductIds = ownedProducts.mapTo(mutableSetOf()) { it.id }

        return ownedProducts
            .flatMap { product ->
                ops.neighbors(
                    product.id,
                    NeighborOptions(edgeLabel = PurchasedLabel.label, direction = Direction.INCOMING, maxDepth = 1)
                ).toList()
            }
            .filterNot { it.id == userId }
            .distinctBy { it.id }
            .flatMap { similarUser ->
                ops.neighbors(
                    similarUser.id,
                    NeighborOptions(edgeLabel = PurchasedLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
                ).toList()
            }
            .filterNot { it.id in ownedProductIds }
            .distinctBy { it.id }
            .take(limit)
            .asFlow()
    }

    /**
     * Recommends follow targets from second-hop follow relationships.
     */
    suspend fun recommendFollows(userId: GraphElementId, limit: Int = 10): Flow<GraphVertex> {
        require(limit > 0) { "limit must be > 0, was $limit" }

        val directFollows = ops.neighbors(
            userId,
            NeighborOptions(edgeLabel = FollowsLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
        ).toList()
        val excludedIds = directFollows.mapTo(mutableSetOf(userId)) { it.id }

        return directFollows
            .flatMap { direct ->
                ops.neighbors(
                    direct.id,
                    NeighborOptions(edgeLabel = FollowsLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
                ).toList()
            }
            .filterNot { it.id in excludedIds }
            .distinctBy { it.id }
            .take(limit)
            .asFlow()
    }

    /**
     * Ranks products by purchase graph PageRank.
     */
    fun rankPopularProducts(limit: Int = 10): Flow<PageRankScore> {
        require(limit > 0) { "limit must be > 0, was $limit" }
        return ops.pageRank(
            PageRankOptions(vertexLabel = ProductLabel.label, edgeLabel = PurchasedLabel.label, topK = limit)
        )
    }
}

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
 * [RecommendationService]의 coroutine 및 Flow 버전이다.
 */
class RecommendationSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "recommendation",
) {
    companion object : KLoggingChannel()

    /**
     * Backing graph가 아직 없으면 생성한다.
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Recommendation graph '$graphName' created" }
        }
    }

    /**
     * User vertex를 추가한다.
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
     * Product vertex를 추가한다.
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
     * User에서 product로 이어지는 purchase edge를 기록한다.
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
     * User 사이의 directed follow edge를 생성한다.
     */
    suspend fun follow(followerId: GraphElementId, targetId: GraphElementId) {
        ops.createEdge(followerId, targetId, FollowsLabel.label, emptyMap())
    }

    /**
     * Source user와 같은 product를 구매한 user들이 산 product를 추천한다.
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
     * Second-hop follow relationship에서 follow target을 추천한다.
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
     * Purchase graph PageRank 기준으로 product 순위를 매긴다.
     */
    fun rankPopularProducts(limit: Int = 10): Flow<PageRankScore> {
        require(limit > 0) { "limit must be > 0, was $limit" }
        return ops.pageRank(
            PageRankOptions(vertexLabel = ProductLabel.label, edgeLabel = PurchasedLabel.label, topK = limit)
        )
    }
}

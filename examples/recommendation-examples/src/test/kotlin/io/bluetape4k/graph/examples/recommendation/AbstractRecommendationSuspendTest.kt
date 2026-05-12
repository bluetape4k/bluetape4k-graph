package io.bluetape4k.graph.examples.recommendation

import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.recommendation.service.RecommendationSuspendService
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractRecommendationSuspendTest {

    companion object : KLogging()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "recommendation_suspend_test"
    protected val service: RecommendationSuspendService by lazy { RecommendationSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runTest {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
    }

    @Test
    fun `recommends products from similar buyers`() = runTest {
        val alice = service.addUser("u-alice", "Alice")
        val bob = service.addUser("u-bob", "Bob")
        val camera = service.addProduct("p-camera", "Camera", "electronics")
        val tripod = service.addProduct("p-tripod", "Tripod", "electronics")

        service.recordPurchase(alice.id, camera.id)
        service.recordPurchase(bob.id, camera.id)
        service.recordPurchase(bob.id, tripod.id)

        val recommendations = service.recommendProducts(alice.id).toList()
        recommendations.shouldNotBeEmpty()
        recommendations.map { it.properties["productId"] } shouldContain "p-tripod"
    }

    @Test
    fun `recommends second hop follow targets`() = runTest {
        val alice = service.addUser("u-alice", "Alice")
        val bob = service.addUser("u-bob", "Bob")
        val carol = service.addUser("u-carol", "Carol")

        service.follow(alice.id, bob.id)
        service.follow(bob.id, carol.id)

        val followTargets = service.recommendFollows(alice.id).toList()
        followTargets.shouldNotBeEmpty()
        followTargets.map { it.properties["userId"] } shouldContain "u-carol"
    }

    @Test
    fun `ranks popular products inside top results`() = runTest {
        val camera = service.addProduct("p-camera", "Camera")
        repeat(3) { index ->
            val user = service.addUser("u-$index", "User $index")
            service.recordPurchase(user.id, camera.id)
        }
        val tripod = service.addProduct("p-tripod", "Tripod")
        service.recordPurchase(service.addUser("u-extra", "Extra").id, tripod.id)

        val productIds = service.rankPopularProducts(limit = 10).toList().map { it.vertex.properties["productId"] }
        productIds.size shouldBeGreaterOrEqualTo 1
        productIds shouldContain "p-camera"
    }
}

package io.bluetape4k.graph.examples.recommendation

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.recommendation.io.RecommendationSampleDatasetLoader
import io.bluetape4k.graph.examples.recommendation.schema.ProductLabel
import io.bluetape4k.graph.examples.recommendation.schema.UserLabel
import io.bluetape4k.graph.examples.recommendation.service.RecommendationService
import io.bluetape4k.graph.examples.recommendation.service.RecommendationSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RecommendationSampleDatasetLoaderTest {

    @Test
    fun `imports graph-io CSV sample dataset into TinkerGraph`() {
        val ops = TinkerGraphOperations()
        val service = RecommendationService(ops)
        service.initialize()

        val report = RecommendationSampleDatasetLoader.importCsv(ops)
        val alice = ops.findVerticesByLabel(UserLabel.label, mapOf(UserLabel.userId.name to "u-alice")).single()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 6L
        report.edgesCreated shouldBeEqualTo 6L
        service.recommendProducts(alice.id).map { it.properties[ProductLabel.productId.name] } shouldContain "p-tripod"
        service.recommendFollows(alice.id).map { it.properties[UserLabel.userId.name] } shouldContain "u-carol"
        service.rankPopularProducts(limit = 3).map { it.vertex.properties[ProductLabel.productId.name] } shouldContain "p-camera"
    }

    @Test
    fun `imports graph-io CSV sample dataset into suspend TinkerGraph`() = runTest {
        val ops = TinkerGraphSuspendOperations()
        val service = RecommendationSuspendService(ops)
        service.initialize()

        val report = RecommendationSampleDatasetLoader.importCsvSuspending(ops)
        val alice = ops.findVerticesByLabel(UserLabel.label, mapOf(UserLabel.userId.name to "u-alice")).toList().single()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 6L
        report.edgesCreated shouldBeEqualTo 6L
        service.recommendProducts(alice.id).toList().map { it.properties[ProductLabel.productId.name] } shouldContain "p-tripod"
        service.recommendFollows(alice.id).toList().map { it.properties[UserLabel.userId.name] } shouldContain "u-carol"
        service.rankPopularProducts(limit = 3).toList().map { it.vertex.properties[ProductLabel.productId.name] } shouldContain "p-camera"
    }

    @Test
    fun `imports default resources when context class loader cannot see examples`() {
        withContextClassLoader(object : ClassLoader(null) {}) {
            val ops = TinkerGraphOperations()
            RecommendationService(ops).initialize()

            val report = RecommendationSampleDatasetLoader.importCsv(ops)

            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 6L
            report.edgesCreated shouldBeEqualTo 6L
        }
    }

    @Test
    fun `throws IllegalArgumentException when sample resource is missing`() {
        assertFailsWith<IllegalArgumentException> {
            RecommendationSampleDatasetLoader.importCsv(
                TinkerGraphOperations(),
                verticesResource = "sample-data/recommendation/missing-vertices.csv",
            )
        }
    }

    private fun <T> withContextClassLoader(classLoader: ClassLoader, block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader

        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }
}

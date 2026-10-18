package io.bluetape4k.graph.examples.recommendation

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.examples.recommendation.io.RecommendationSampleDatasetLoader
import io.bluetape4k.graph.examples.recommendation.schema.ProductLabel
import io.bluetape4k.graph.examples.recommendation.schema.UserLabel
import io.bluetape4k.graph.examples.recommendation.service.RecommendationService
import io.bluetape4k.graph.examples.recommendation.service.RecommendationSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class RecommendationSampleDatasetLoaderTest {

    companion object: KLogging()

    @Test
    fun `imports graph-io CSV sample dataset into TinkerGraph`() {
        val ops = TinkerGraphOperations()
        val service = RecommendationService(ops)
        service.initialize()

        val report = RecommendationSampleDatasetLoader.importCsv(ops)
        val alice = ops.findVerticesByLabel(UserLabel.label, mapOf(UserLabel.userId.name to "u-alice")).single()

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 6L
        report.edgesCreated shouldBeEqualTo 6L

        service.recommendProducts(alice.id)
            .onEach { log.debug { "recommend Product=$it" } }
            .map { it.properties[ProductLabel.productId.name] } shouldContain "p-tripod"

        service.recommendFollows(alice.id)
            .onEach { log.debug { "recommend Follow=$it" } }
            .map { it.properties[UserLabel.userId.name] } shouldContain "u-carol"

        service.rankPopularProducts(limit = 3)
            .onEach { log.debug { "rank popular products=$it" } }
            .map { it.vertex.properties[ProductLabel.productId.name] } shouldContain "p-camera"
    }

    @Test
    fun `imports graph-io CSV sample dataset into suspend TinkerGraph`() = runTest {
        val ops = TinkerGraphSuspendOperations()
        val service = RecommendationSuspendService(ops)
        service.initialize()

        val report = RecommendationSampleDatasetLoader.importCsvSuspending(ops)
        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 6L
        report.edgesCreated shouldBeEqualTo 6L

        val alice = ops.findVerticesByLabel(UserLabel.label, UserLabel.userId.name to "u-alice")
            .log("Vertex")
            .toList().single()

        service.recommendProducts(alice.id)
            .log("Product")
            .map { it.properties[ProductLabel.productId.name] }
            .toList() shouldContain "p-tripod"

        service.recommendFollows(alice.id)
            .log("Follow")
            .map { it.properties[UserLabel.userId.name] }
            .toList() shouldContain "u-carol"

        service.rankPopularProducts(limit = 3)
            .log("Rank")
            .map { it.vertex.properties[ProductLabel.productId.name] }
            .toList() shouldContain "p-camera"
    }

    @Test
    fun `imports default resources when context class loader cannot see examples`() {
        withContextClassLoader(object: ClassLoader(null) {}) {
            val ops = TinkerGraphOperations()
            RecommendationService(ops).initialize()

            val report = RecommendationSampleDatasetLoader.importCsv(ops)

            log.debug { "report=$report" }
            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 6L
            report.edgesCreated shouldBeEqualTo 6L
        }
    }

    @Test
    fun `imports packaged resources with null context class loader and restores it`() {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader

        val report = withContextClassLoader(null) {
            TinkerGraphOperations().use { ops ->
                RecommendationSampleDatasetLoader.importCsv(ops)
            }
        }

        thread.contextClassLoader shouldBeEqualTo previous

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesRead shouldBeGreaterThan 0L
        report.verticesCreated shouldBeGreaterThan 0L
        report.edgesRead shouldBeGreaterThan 0L
        report.edgesCreated shouldBeGreaterThan 0L
    }

    @Test
    fun `suspending import uses fallback with null context class loader and restores it`() = runSuspendIO {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { caller ->
            withContext(caller) {
                val thread = Thread.currentThread()
                val previous = thread.contextClassLoader
                val report = withSuspendingContextClassLoader(null) {
                    TinkerGraphSuspendOperations().use { ops ->
                        RecommendationSampleDatasetLoader.importCsvSuspending(ops)
                    }
                }

                thread.contextClassLoader shouldBeEqualTo previous

                log.debug { "report=$report" }
                report.status shouldBeEqualTo GraphIoStatus.COMPLETED
                report.verticesRead shouldBeGreaterThan 0L
                report.verticesCreated shouldBeGreaterThan 0L
                report.edgesRead shouldBeGreaterThan 0L
                report.edgesCreated shouldBeGreaterThan 0L
            }
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

    private fun <T> withContextClassLoader(classLoader: ClassLoader?, block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader

        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }

    private suspend fun <T> withSuspendingContextClassLoader(
        classLoader: ClassLoader?,
        block: suspend () -> T,
    ): T {
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

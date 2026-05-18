package io.bluetape4k.graph.examples.fraud

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.fraud.io.FraudDetectionSampleDatasetLoader
import io.bluetape4k.graph.examples.fraud.schema.AccountLabel
import io.bluetape4k.graph.examples.fraud.service.FraudDetectionService
import io.bluetape4k.graph.examples.fraud.service.FraudDetectionSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FraudDetectionSampleDatasetLoaderTest {

    @Test
    fun `imports graph-io CSV sample dataset into TinkerGraph`() {
        val ops = TinkerGraphOperations()
        val service = FraudDetectionService(ops)
        service.initialize()

        val report = FraudDetectionSampleDatasetLoader.importCsv(ops)

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 3L
        ops.findVerticesByLabel(AccountLabel.label).map { it.properties[AccountLabel.accountId.name] } shouldContain "acct-a"
        service.detectCircularTransfers(maxDepth = 5).shouldNotBeEmpty()
        service.detectSuspiciousClusters(minSize = 3).shouldNotBeEmpty()
        service.rankHighRiskAccounts(limit = 3).shouldNotBeEmpty()
    }

    @Test
    fun `imports graph-io CSV sample dataset into suspend TinkerGraph`() = runTest {
        val ops = TinkerGraphSuspendOperations()
        val service = FraudDetectionSuspendService(ops)
        service.initialize()

        val report = FraudDetectionSampleDatasetLoader.importCsvSuspending(ops)

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 3L
        service.detectCircularTransfers(maxDepth = 5).toList().shouldNotBeEmpty()
        service.detectSuspiciousClusters(minSize = 3).toList().shouldNotBeEmpty()
        service.rankHighRiskAccounts(limit = 3).toList().shouldNotBeEmpty()
    }

    @Test
    fun `imports default resources when context class loader cannot see examples`() {
        withContextClassLoader(object : ClassLoader(null) {}) {
            val ops = TinkerGraphOperations()
            FraudDetectionService(ops).initialize()

            val report = FraudDetectionSampleDatasetLoader.importCsv(ops)

            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 3L
            report.edgesCreated shouldBeEqualTo 3L
        }
    }

    @Test
    fun `throws IllegalArgumentException when sample resource is missing`() {
        assertFailsWith<IllegalArgumentException> {
            FraudDetectionSampleDatasetLoader.importCsv(
                TinkerGraphOperations(),
                verticesResource = "sample-data/fraud/missing-vertices.csv",
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

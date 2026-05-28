package io.bluetape4k.graph.examples.observability

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.observability.io.ObservabilitySampleDatasetLoader
import io.bluetape4k.graph.examples.observability.schema.ApiLabel
import io.bluetape4k.graph.examples.observability.schema.ServiceLabel
import io.bluetape4k.graph.examples.observability.schema.TeamLabel
import io.bluetape4k.graph.examples.observability.service.ObservabilityIncidentService
import io.bluetape4k.graph.examples.observability.service.ObservabilityIncidentSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ObservabilitySampleDatasetLoaderTest {

    @Test
    fun `imports graph-io CSV sample dataset into TinkerGraph`() {
        val ops = TinkerGraphOperations()
        val service = ObservabilityIncidentService(ops)
        service.initialize()

        val report = ObservabilitySampleDatasetLoader.importCsv(ops)

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 10L
        report.edgesCreated shouldBeEqualTo 10L
        ops.findVerticesByLabel(ServiceLabel.label).map { it.properties[ServiceLabel.serviceId.name] } shouldContain
            "payment-service"
        service.affectedApis("payment-service").map { it.properties[ApiLabel.apiId.name] } shouldContain "checkout-api"
        service.owningTeams("payment-service").map { it.properties[TeamLabel.teamId.name] } shouldContain "payments-team"
        service.alertBoundary(listOf("payment-latency", "checkout-errors")).shouldNotBeEmpty()
    }

    @Test
    fun `imports graph-io CSV sample dataset into suspend TinkerGraph`() = runTest {
        val ops = TinkerGraphSuspendOperations()
        val service = ObservabilityIncidentSuspendService(ops)
        service.initialize()

        val report = ObservabilitySampleDatasetLoader.importCsvSuspending(ops)

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 10L
        report.edgesCreated shouldBeEqualTo 10L
        service.affectedApis("payment-service").map { it.properties[ApiLabel.apiId.name] } shouldContain
            "mobile-checkout-api"
        service.owningTeams("payment-service").map { it.properties[TeamLabel.teamId.name] } shouldContain "payments-team"
        service.alertBoundary(listOf("payment-latency", "checkout-errors")).shouldNotBeEmpty()
    }

    @Test
    fun `imports default resources when context class loader cannot see examples`() {
        withContextClassLoader(object : ClassLoader(null) {}) {
            val ops = TinkerGraphOperations()
            ObservabilityIncidentService(ops).initialize()

            val report = ObservabilitySampleDatasetLoader.importCsv(ops)

            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 10L
            report.edgesCreated shouldBeEqualTo 10L
        }
    }

    @Test
    fun `throws IllegalArgumentException when sample resource is missing`() {
        assertFailsWith<IllegalArgumentException> {
            ObservabilitySampleDatasetLoader.importCsv(
                TinkerGraphOperations(),
                verticesResource = "sample-data/observability/missing-vertices.csv",
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

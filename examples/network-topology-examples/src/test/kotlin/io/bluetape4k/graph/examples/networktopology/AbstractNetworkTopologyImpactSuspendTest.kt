package io.bluetape4k.graph.examples.networktopology

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.networktopology.io.NetworkTopologySampleDatasetLoader
import io.bluetape4k.graph.examples.networktopology.schema.ServiceLabel
import io.bluetape4k.graph.examples.networktopology.service.NetworkTopologyImpactSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractNetworkTopologyImpactSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "network_topology_suspend_test"
    protected val service: NetworkTopologyImpactSuspendService by lazy {
        NetworkTopologyImpactSuspendService(ops, graphName)
    }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()

        val importReport = NetworkTopologySampleDatasetLoader.importCsvSuspending(ops)
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds suspend service path`() = runSuspendIO {
        val path = service.shortestPathToService("svc-checkout")

        log.debug { "path=$path" }
        path?.deviceIds shouldBeEqualTo listOf("router-core", "router-edge-a", "switch-access-a", "svc-checkout")
    }

    @Test
    fun `finds suspend impact and redundant routes`() = runSuspendIO {
        val impactedServices = service.impactedServicesByFailedLink("link-b-iot")
            .map { it.properties[ServiceLabel.serviceId.name] }

        log.debug { "impactedServices=$impactedServices" }
        impactedServices shouldContain "svc-cameras"

        val redundantPaths = service.redundantDevicePaths("router-core", "switch-access-a")
            .map { it.deviceIds }

        log.debug { "redundantPaths=$redundantPaths" }
        redundantPaths shouldContain listOf(
            "router-core",
            "router-edge-b",
            "switch-access-b",
            "switch-access-a",
        )
    }
}

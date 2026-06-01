package io.bluetape4k.graph.examples.networktopology

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.networktopology.io.NetworkTopologySampleDatasetLoader
import io.bluetape4k.graph.examples.networktopology.schema.SegmentLabel
import io.bluetape4k.graph.examples.networktopology.schema.ServiceLabel
import io.bluetape4k.graph.examples.networktopology.service.NetworkTopologyImpactService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractNetworkTopologyImpactTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "network_topology_test"
    protected val service: NetworkTopologyImpactService by lazy { NetworkTopologyImpactService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        NetworkTopologySampleDatasetLoader.importCsv(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds shortest device and service paths`() {
        val devicePath = service.shortestDevicePath("router-core", "switch-access-a")
        val servicePath = service.shortestPathToService("svc-checkout")

        devicePath?.deviceIds shouldBeEqualTo listOf("router-core", "router-edge-a", "switch-access-a")
        servicePath?.deviceIds shouldBeEqualTo listOf(
            "router-core",
            "router-edge-a",
            "switch-access-a",
            "svc-checkout",
        )
    }

    @Test
    fun `finds service impact from failed device and link`() {
        val failedDeviceServices = service.impactedServicesByFailedDevice("switch-iot")
            .map { it.properties[ServiceLabel.serviceId.name] }
        val failedLinkServices = service.impactedServicesByFailedLink("link-b-iot")
            .map { it.properties[ServiceLabel.serviceId.name] }
        val redundantLinkServices = service.impactedServicesByFailedLink("link-edge-a-access-a")
            .map { it.properties[ServiceLabel.serviceId.name] }

        failedDeviceServices shouldContain "svc-cameras"
        failedLinkServices shouldContain "svc-cameras"
        redundantLinkServices shouldBeEqualTo emptyList()
    }

    @Test
    fun `detects isolated segments`() {
        val segmentIds = service.isolatedSegments()
            .map { it.properties[SegmentLabel.segmentId.name] }

        segmentIds shouldBeEqualTo listOf("seg-lab")
    }

    @Test
    fun `discovers redundant route candidates`() {
        val paths = service.redundantDevicePaths("router-core", "switch-access-a")
            .map { it.deviceIds }

        paths shouldContain listOf("router-core", "router-edge-a", "switch-access-a")
        paths shouldContain listOf(
            "router-core",
            "router-edge-b",
            "switch-access-b",
            "switch-access-a",
        )
    }
}

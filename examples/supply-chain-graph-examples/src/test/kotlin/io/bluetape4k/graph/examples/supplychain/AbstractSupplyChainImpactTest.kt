package io.bluetape4k.graph.examples.supplychain

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.supplychain.io.SupplyChainSampleDatasetLoader
import io.bluetape4k.graph.examples.supplychain.schema.CustomerOrderLabel
import io.bluetape4k.graph.examples.supplychain.schema.PartLabel
import io.bluetape4k.graph.examples.supplychain.schema.RouteLabel
import io.bluetape4k.graph.examples.supplychain.service.SupplyChainImpactService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSupplyChainImpactTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "supply_chain_test"
    protected val service: SupplyChainImpactService by lazy { SupplyChainImpactService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        SupplyChainSampleDatasetLoader.importCsv(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds supplier impact to customer orders`() {
        val orderIds = service.impactedOrdersBySupplier("supplier-alpha")
            .map { it.properties[CustomerOrderLabel.orderId.name] }

        orderIds shouldContain "order-1001"
    }

    @Test
    fun `finds part impact across multiple orders`() {
        val orderIds = service.impactedOrdersByPart("gps-module")
            .map { it.properties[CustomerOrderLabel.orderId.name] }

        orderIds shouldContain "order-1001"
        orderIds shouldContain "order-1002"
    }

    @Test
    fun `finds route impact and alternate route candidates`() {
        val impacted = service.impactedOrdersByRoute("route-pacific")
            .map { it.properties[CustomerOrderLabel.orderId.name] }
        val alternates = service.alternateRoutesForOrder("order-1001", failedRouteId = "route-pacific")
            .map { it.properties[RouteLabel.routeId.name] }

        impacted shouldContain "order-1001"
        alternates shouldContain "route-air-express"
    }

    @Test
    fun `detects bottleneck parts and substitution cycles`() {
        val bottlenecks = service.bottleneckParts()
            .map { it.properties[PartLabel.partId.name] }
        val cycles = service.partSubstitutionCycles()

        bottlenecks shouldContain "gps-module"
        cycles shouldContain listOf("battery-cell", "battery-module")
    }
}

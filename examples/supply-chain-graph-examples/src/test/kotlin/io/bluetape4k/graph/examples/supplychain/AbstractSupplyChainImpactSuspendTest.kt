package io.bluetape4k.graph.examples.supplychain

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.supplychain.io.SupplyChainSampleDatasetLoader
import io.bluetape4k.graph.examples.supplychain.schema.CustomerOrderLabel
import io.bluetape4k.graph.examples.supplychain.schema.RouteLabel
import io.bluetape4k.graph.examples.supplychain.service.SupplyChainImpactSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSupplyChainImpactSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "supply_chain_suspend_test"
    protected val service: SupplyChainImpactSuspendService by lazy { SupplyChainImpactSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        SupplyChainSampleDatasetLoader.importCsvSuspending(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds suspend supplier impact`() = runSuspendIO {
        val orderIds = service.impactedOrdersBySupplier("supplier-alpha")
            .map { it.properties[CustomerOrderLabel.orderId.name] }

        orderIds shouldContain "order-1001"
    }

    @Test
    fun `finds suspend alternate route candidate`() = runSuspendIO {
        val alternates = service.alternateRoutesForOrder("order-1001", failedRouteId = "route-pacific")
            .map { it.properties[RouteLabel.routeId.name] }

        alternates shouldContain "route-air-express"
    }
}

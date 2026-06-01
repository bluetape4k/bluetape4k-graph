package io.bluetape4k.graph.examples.datalineage

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.datalineage.io.DataLineageSampleDatasetLoader
import io.bluetape4k.graph.examples.datalineage.schema.DashboardLabel
import io.bluetape4k.graph.examples.datalineage.schema.OwnerLabel
import io.bluetape4k.graph.examples.datalineage.service.DataLineageImpactSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractDataLineageImpactSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "data_lineage_suspend_test"
    protected val service: DataLineageImpactSuspendService by lazy { DataLineageImpactSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        DataLineageSampleDatasetLoader.importCsvSuspending(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds suspend downstream dashboard impact`() = runSuspendIO {
        val dashboardIds = service.impactedDashboardsBySourceTable("raw.orders")
            .map { it.properties[DashboardLabel.dashboardId.name] }

        dashboardIds shouldContain "exec-revenue"
        dashboardIds shouldContain "ops-quality"
    }

    @Test
    fun `finds suspend owner and missing path cases`() = runSuspendIO {
        val ownerIds = service.ownersForBrokenJob("build-revenue-mart")
            .map { it.properties[OwnerLabel.ownerId.name] }
        val missing = service.explainLineagePath("raw.payments", "ops-quality")

        ownerIds shouldContain "finance-analytics"
        missing shouldBeEqualTo emptyList()
    }
}

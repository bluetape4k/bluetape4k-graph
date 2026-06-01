package io.bluetape4k.graph.examples.datalineage

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.datalineage.io.DataLineageSampleDatasetLoader
import io.bluetape4k.graph.examples.datalineage.schema.DashboardLabel
import io.bluetape4k.graph.examples.datalineage.schema.OwnerLabel
import io.bluetape4k.graph.examples.datalineage.schema.TableLabel
import io.bluetape4k.graph.examples.datalineage.service.DataLineageImpactService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractDataLineageImpactTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "data_lineage_test"
    protected val service: DataLineageImpactService by lazy { DataLineageImpactService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        DataLineageSampleDatasetLoader.importCsv(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds downstream dashboard impact for source table and column`() {
        val tableImpact = service.impactedDashboardsBySourceTable("raw.orders")
            .map { it.properties[DashboardLabel.dashboardId.name] }
        val columnImpact = service.impactedDashboardsByColumn("raw.orders.customer_id")
            .map { it.properties[DashboardLabel.dashboardId.name] }

        tableImpact shouldContain "exec-revenue"
        tableImpact shouldContain "ops-quality"
        columnImpact shouldContain "exec-revenue"
        columnImpact shouldContain "ops-quality"
    }

    @Test
    fun `finds upstream tables for a dashboard metric`() {
        val tableIds = service.upstreamTablesForDashboard("exec-revenue")
            .map { it.properties[TableLabel.tableId.name] }

        tableIds shouldContain "mart.revenue_daily"
        tableIds shouldContain "curated.orders_enriched"
        tableIds shouldContain "raw.orders"
        tableIds shouldContain "raw.payments"
    }

    @Test
    fun `finds owners for broken jobs and quality failures`() {
        val ownerIds = service.ownersForBrokenJob("build-revenue-mart")
            .map { it.properties[OwnerLabel.ownerId.name] }
        val affectedDashboards = service.dashboardsAffectedByQualityCheck("check-orders-customer")
            .map { it.properties[DashboardLabel.dashboardId.name] }

        ownerIds shouldContain "finance-analytics"
        affectedDashboards shouldContain "exec-revenue"
        affectedDashboards shouldContain "ops-quality"
    }

    @Test
    fun `explains bounded lineage path and returns empty when missing`() {
        val paths = service.explainLineagePath("raw.orders", "exec-revenue")
            .map { it.nodeIds }
        val missing = service.explainLineagePath("raw.payments", "ops-quality")

        paths shouldContain listOf(
            "raw.orders",
            "ingest-orders",
            "curated.orders_enriched",
            "build-revenue-mart",
            "mart.revenue_daily",
            "exec-revenue",
        )
        missing shouldBeEqualTo emptyList()
    }
}

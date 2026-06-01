package io.bluetape4k.graph.examples.datalineage.service

import io.bluetape4k.graph.examples.datalineage.schema.ColumnInputToJobLabel
import io.bluetape4k.graph.examples.datalineage.schema.ColumnLabel
import io.bluetape4k.graph.examples.datalineage.schema.DashboardLabel
import io.bluetape4k.graph.examples.datalineage.schema.FeedsDashboardLabel
import io.bluetape4k.graph.examples.datalineage.schema.JobOutputsTableLabel
import io.bluetape4k.graph.examples.datalineage.schema.OwnerLabel
import io.bluetape4k.graph.examples.datalineage.schema.OwnsDashboardLabel
import io.bluetape4k.graph.examples.datalineage.schema.OwnsJobLabel
import io.bluetape4k.graph.examples.datalineage.schema.PipelineJobLabel
import io.bluetape4k.graph.examples.datalineage.schema.QualityCheckLabel
import io.bluetape4k.graph.examples.datalineage.schema.TableInputToJobLabel
import io.bluetape4k.graph.examples.datalineage.schema.TableLabel
import io.bluetape4k.graph.examples.datalineage.schema.ValidatesColumnLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Explains one bounded data-lineage path.
 */
data class LineageImpactPath(
    val nodeIds: List<String>,
    val reason: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Data-lineage impact analysis service built on top of [GraphOperations].
 *
 * The service keeps the example intentionally compact: it models datasets, tables, columns, jobs, dashboards, owners,
 * and checks, then answers bounded traversal questions commonly used before pipeline or schema changes.
 */
class DataLineageImpactService(
    private val ops: GraphOperations,
    private val graphName: String = "data_lineage",
) {
    companion object: KLogging()

    /**
     * Creates the backing graph when it does not already exist.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Data-lineage graph '$graphName' created" }
        }
    }

    fun impactedDashboardsBySourceTable(tableId: String): List<GraphVertex> {
        tableId.requireNotBlank("tableId")
        val table = tableById(tableId) ?: return emptyList()
        return dashboardsDownstreamFromTable(table)
    }

    fun impactedDashboardsByColumn(columnId: String): List<GraphVertex> {
        columnId.requireNotBlank("columnId")
        val column = columnById(columnId) ?: return emptyList()
        return outgoing(column, ColumnInputToJobLabel.label, PipelineJobLabel.label)
            .flatMap { job -> outgoing(job, JobOutputsTableLabel.label, TableLabel.label) }
            .flatMap(::dashboardsDownstreamFromTable)
            .distinctBy { it.id }
    }

    fun upstreamTablesForDashboard(dashboardId: String): List<GraphVertex> {
        dashboardId.requireNotBlank("dashboardId")
        val dashboard = dashboardById(dashboardId) ?: return emptyList()
        val metricTables = incoming(dashboard, FeedsDashboardLabel.label, TableLabel.label)
        val producingJobs = metricTables.flatMap { table ->
            incoming(table, JobOutputsTableLabel.label, PipelineJobLabel.label)
        }
        val directInputs = producingJobs.flatMap { job ->
            incoming(job, TableInputToJobLabel.label, TableLabel.label)
        }
        val previousJobs = directInputs.flatMap { table ->
            incoming(table, JobOutputsTableLabel.label, PipelineJobLabel.label)
        }
        val previousInputs = previousJobs.flatMap { job ->
            incoming(job, TableInputToJobLabel.label, TableLabel.label)
        }

        return (metricTables + directInputs + previousInputs).distinctBy { it.id }
    }

    fun ownersForBrokenJob(jobId: String): List<GraphVertex> {
        jobId.requireNotBlank("jobId")
        val job = jobById(jobId) ?: return emptyList()
        val jobOwners = incoming(job, OwnsJobLabel.label, OwnerLabel.label)
        val dashboardOwners = outgoing(job, JobOutputsTableLabel.label, TableLabel.label)
            .flatMap(::dashboardsDownstreamFromTable)
            .flatMap { dashboard -> incoming(dashboard, OwnsDashboardLabel.label, OwnerLabel.label) }

        return (jobOwners + dashboardOwners).distinctBy { it.id }
    }

    fun dashboardsAffectedByQualityCheck(checkId: String): List<GraphVertex> {
        checkId.requireNotBlank("checkId")
        val check = qualityCheckById(checkId) ?: return emptyList()
        return outgoing(check, ValidatesColumnLabel.label, ColumnLabel.label)
            .flatMap { column -> impactedDashboardsByColumn(columnId(column)) }
            .distinctBy { it.id }
    }

    fun explainLineagePath(
        sourceTableId: String,
        dashboardId: String,
        maxDepth: Int = 5,
    ): List<LineageImpactPath> {
        sourceTableId.requireNotBlank("sourceTableId")
        dashboardId.requireNotBlank("dashboardId")
        require(maxDepth > 0) { "maxDepth must be > 0, was $maxDepth" }

        val source = tableById(sourceTableId) ?: return emptyList()
        val target = dashboardById(dashboardId) ?: return emptyList()
        val results = mutableListOf<LineageImpactPath>()
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (path.size > maxDepth + 1) {
                continue
            }
            if (tail.id == target.id) {
                results += LineageImpactPath(path.map(::businessId), "source table feeds dashboard through lineage")
                continue
            }
            lineageOutgoing(tail)
                .filterNot { next -> path.any { it.id == next.id } }
                .forEach { next -> queue += path + next }
        }

        return results
    }

    private fun dashboardsDownstreamFromTable(table: GraphVertex): List<GraphVertex> {
        val directDashboards = outgoing(table, FeedsDashboardLabel.label, DashboardLabel.label)
        val firstHopTables = outgoing(table, TableInputToJobLabel.label, PipelineJobLabel.label)
            .flatMap { job -> outgoing(job, JobOutputsTableLabel.label, TableLabel.label) }
        val secondHopTables = firstHopTables
            .flatMap { downstream -> outgoing(downstream, TableInputToJobLabel.label, PipelineJobLabel.label) }
            .flatMap { job -> outgoing(job, JobOutputsTableLabel.label, TableLabel.label) }

        return (directDashboards + firstHopTables.flatMap(::dashboardsDownstreamFromTableDirect) +
            secondHopTables.flatMap(::dashboardsDownstreamFromTableDirect))
            .distinctBy { it.id }
    }

    private fun dashboardsDownstreamFromTableDirect(table: GraphVertex): List<GraphVertex> =
        outgoing(table, FeedsDashboardLabel.label, DashboardLabel.label)

    private fun lineageOutgoing(vertex: GraphVertex): List<GraphVertex> = when (vertex.label) {
        TableLabel.label -> outgoing(vertex, TableInputToJobLabel.label, PipelineJobLabel.label) +
            outgoing(vertex, FeedsDashboardLabel.label, DashboardLabel.label)
        ColumnLabel.label -> outgoing(vertex, ColumnInputToJobLabel.label, PipelineJobLabel.label)
        PipelineJobLabel.label -> outgoing(vertex, JobOutputsTableLabel.label, TableLabel.label)
        else -> emptyList()
    }

    private fun outgoing(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.OUTGOING, maxDepth = 1)
        ).filter { it.label == vertexLabel }

    private fun incoming(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.INCOMING, maxDepth = 1)
        ).filter { it.label == vertexLabel }

    private fun tableById(tableId: String): GraphVertex? =
        ops.findVerticesByLabel(TableLabel.label, mapOf(TableLabel.tableId.name to tableId)).firstOrNull()

    private fun columnById(columnId: String): GraphVertex? =
        ops.findVerticesByLabel(ColumnLabel.label, mapOf(ColumnLabel.columnId.name to columnId)).firstOrNull()

    private fun jobById(jobId: String): GraphVertex? =
        ops.findVerticesByLabel(PipelineJobLabel.label, mapOf(PipelineJobLabel.jobId.name to jobId)).firstOrNull()

    private fun dashboardById(dashboardId: String): GraphVertex? =
        ops.findVerticesByLabel(DashboardLabel.label, mapOf(DashboardLabel.dashboardId.name to dashboardId))
            .firstOrNull()

    private fun qualityCheckById(checkId: String): GraphVertex? =
        ops.findVerticesByLabel(QualityCheckLabel.label, mapOf(QualityCheckLabel.checkId.name to checkId)).firstOrNull()

    private fun columnId(column: GraphVertex): String =
        column.properties[ColumnLabel.columnId.name].toString()

    private fun businessId(vertex: GraphVertex): String = when (vertex.label) {
        TableLabel.label -> vertex.properties[TableLabel.tableId.name].toString()
        ColumnLabel.label -> vertex.properties[ColumnLabel.columnId.name].toString()
        PipelineJobLabel.label -> vertex.properties[PipelineJobLabel.jobId.name].toString()
        DashboardLabel.label -> vertex.properties[DashboardLabel.dashboardId.name].toString()
        OwnerLabel.label -> vertex.properties[OwnerLabel.ownerId.name].toString()
        QualityCheckLabel.label -> vertex.properties[QualityCheckLabel.checkId.name].toString()
        else -> vertex.id.value
    }
}

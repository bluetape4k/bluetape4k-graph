package io.bluetape4k.graph.examples.datalineage.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Dataset vertices group related physical tables.
 */
object DatasetLabel: VertexLabel("Dataset") {
    val datasetId = string("datasetId")
    val name = string("name")
    val domain = string("domain")
    val status = string("status")
}

/**
 * Table vertices represent source, curated, and mart assets.
 */
object TableLabel: VertexLabel("Table") {
    val tableId = string("tableId")
    val name = string("name")
    val domain = string("domain")
    val status = string("status")
}

/**
 * Column vertices allow column-level impact examples.
 */
object ColumnLabel: VertexLabel("Column") {
    val columnId = string("columnId")
    val name = string("name")
    val dataType = string("dataType")
    val status = string("status")
}

/**
 * Pipeline job vertices transform upstream tables into downstream assets.
 */
object PipelineJobLabel: VertexLabel("PipelineJob") {
    val jobId = string("jobId")
    val name = string("name")
    val schedule = string("schedule")
    val status = string("status")
}

/**
 * Dashboard vertices represent business-facing metrics.
 */
object DashboardLabel: VertexLabel("Dashboard") {
    val dashboardId = string("dashboardId")
    val name = string("name")
    val metric = string("metric")
    val status = string("status")
}

/**
 * Owner vertices represent teams accountable for assets and jobs.
 */
object OwnerLabel: VertexLabel("Owner") {
    val ownerId = string("ownerId")
    val name = string("name")
    val team = string("team")
    val channel = string("channel")
}

/**
 * Data quality check vertices model failing controls.
 */
object QualityCheckLabel: VertexLabel("QualityCheck") {
    val checkId = string("checkId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Dataset-to-table containment edge.
 */
object ContainsTableLabel: EdgeLabel("CONTAINS_TABLE", DatasetLabel, TableLabel) {
    val kind = string("kind")
}

/**
 * Table-to-column containment edge.
 */
object ContainsColumnLabel: EdgeLabel("CONTAINS_COLUMN", TableLabel, ColumnLabel) {
    val kind = string("kind")
}

/**
 * Table input edge into a pipeline job.
 */
object TableInputToJobLabel: EdgeLabel("INPUT_TO_JOB", TableLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Column input edge into a pipeline job.
 */
object ColumnInputToJobLabel: EdgeLabel("COLUMN_INPUT_TO_JOB", ColumnLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Pipeline output edge to a downstream table.
 */
object JobOutputsTableLabel: EdgeLabel("OUTPUTS_TABLE", PipelineJobLabel, TableLabel) {
    val kind = string("kind")
}

/**
 * Table-to-dashboard metric dependency edge.
 */
object FeedsDashboardLabel: EdgeLabel("FEEDS_DASHBOARD", TableLabel, DashboardLabel) {
    val kind = string("kind")
}

/**
 * Owner-to-job accountability edge.
 */
object OwnsJobLabel: EdgeLabel("OWNS_JOB", OwnerLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Owner-to-dashboard accountability edge.
 */
object OwnsDashboardLabel: EdgeLabel("OWNS_DASHBOARD", OwnerLabel, DashboardLabel) {
    val kind = string("kind")
}

/**
 * Data quality check to column validation edge.
 */
object ValidatesColumnLabel: EdgeLabel("VALIDATES_COLUMN", QualityCheckLabel, ColumnLabel) {
    val kind = string("kind")
}

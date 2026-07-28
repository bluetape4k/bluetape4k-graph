package io.bluetape4k.graph.examples.datalineage.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Dataset vertex는 관련 physical table을 묶는다.
 */
object DatasetLabel: VertexLabel("Dataset") {
    val datasetId = string("datasetId")
    val name = string("name")
    val domain = string("domain")
    val status = string("status")
}

/**
 * Table vertex는 source, curated, mart asset을 표현한다.
 */
object TableLabel: VertexLabel("Table") {
    val tableId = string("tableId")
    val name = string("name")
    val domain = string("domain")
    val status = string("status")
}

/**
 * Column vertex는 column-level impact example을 가능하게 한다.
 */
object ColumnLabel: VertexLabel("Column") {
    val columnId = string("columnId")
    val name = string("name")
    val dataType = string("dataType")
    val status = string("status")
}

/**
 * Pipeline job vertex는 upstream table을 downstream asset으로 변환한다.
 */
object PipelineJobLabel: VertexLabel("PipelineJob") {
    val jobId = string("jobId")
    val name = string("name")
    val schedule = string("schedule")
    val status = string("status")
}

/**
 * Dashboard vertex는 business-facing metric을 표현한다.
 */
object DashboardLabel: VertexLabel("Dashboard") {
    val dashboardId = string("dashboardId")
    val name = string("name")
    val metric = string("metric")
    val status = string("status")
}

/**
 * Owner vertex는 asset과 job에 책임지는 team을 표현한다.
 */
object OwnerLabel: VertexLabel("Owner") {
    val ownerId = string("ownerId")
    val name = string("name")
    val team = string("team")
    val channel = string("channel")
}

/**
 * Data quality check vertex는 실패한 control을 모델링한다.
 */
object QualityCheckLabel: VertexLabel("QualityCheck") {
    val checkId = string("checkId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Dataset에서 table로 이어지는 containment edge이다.
 */
object ContainsTableLabel: EdgeLabel("CONTAINS_TABLE", DatasetLabel, TableLabel) {
    val kind = string("kind")
}

/**
 * Table에서 column으로 이어지는 containment edge이다.
 */
object ContainsColumnLabel: EdgeLabel("CONTAINS_COLUMN", TableLabel, ColumnLabel) {
    val kind = string("kind")
}

/**
 * Pipeline job으로 들어가는 table input edge이다.
 */
object TableInputToJobLabel: EdgeLabel("INPUT_TO_JOB", TableLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Pipeline job으로 들어가는 column input edge이다.
 */
object ColumnInputToJobLabel: EdgeLabel("COLUMN_INPUT_TO_JOB", ColumnLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Downstream table로 이어지는 pipeline output edge이다.
 */
object JobOutputsTableLabel: EdgeLabel("OUTPUTS_TABLE", PipelineJobLabel, TableLabel) {
    val kind = string("kind")
}

/**
 * Table에서 dashboard로 이어지는 metric dependency edge이다.
 */
object FeedsDashboardLabel: EdgeLabel("FEEDS_DASHBOARD", TableLabel, DashboardLabel) {
    val kind = string("kind")
}

/**
 * Owner에서 job으로 이어지는 accountability edge이다.
 */
object OwnsJobLabel: EdgeLabel("OWNS_JOB", OwnerLabel, PipelineJobLabel) {
    val kind = string("kind")
}

/**
 * Owner에서 dashboard로 이어지는 accountability edge이다.
 */
object OwnsDashboardLabel: EdgeLabel("OWNS_DASHBOARD", OwnerLabel, DashboardLabel) {
    val kind = string("kind")
}

/**
 * Data quality check에서 column으로 이어지는 validation edge이다.
 */
object ValidatesColumnLabel: EdgeLabel("VALIDATES_COLUMN", QualityCheckLabel, ColumnLabel) {
    val kind = string("kind")
}

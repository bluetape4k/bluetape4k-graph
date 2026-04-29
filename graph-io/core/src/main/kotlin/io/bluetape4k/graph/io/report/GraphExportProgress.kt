package io.bluetape4k.graph.io.report

/** 그래프 익스포트 진행 상태 스냅샷 */
data class GraphExportProgress(
    val exported: Long,
    val total: Long? = null,
    val currentLabel: String? = null,
    val throughputPerSec: Double? = null,
)

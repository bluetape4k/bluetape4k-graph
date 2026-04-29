package io.bluetape4k.graph.io.report

/** 그래프 임포트 진행 상태 스냅샷 */
data class GraphImportProgress(
    val processed: Long,
    val total: Long? = null,
    val currentLabel: String? = null,
    val throughputPerSec: Double? = null,
)

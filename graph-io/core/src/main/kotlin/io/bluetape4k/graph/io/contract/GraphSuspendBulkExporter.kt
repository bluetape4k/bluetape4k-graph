package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

/** 코루틴 기반 suspend 벌크 익스포터 계약. */
interface GraphSuspendBulkExporter<T : Any> {
    suspend fun exportGraphSuspending(
        sink: T,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}

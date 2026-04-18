package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

/** 공통 동기 벌크 익스포터 계약. `T`는 포맷별 싱크 타입. */
interface GraphBulkExporter<T : Any> {
    fun exportGraph(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}

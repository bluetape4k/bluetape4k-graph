package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

/** 코루틴 기반 suspend 벌크 임포터 계약. */
interface GraphSuspendBulkImporter<S : Any> {
    suspend fun importGraphSuspending(
        source: S,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

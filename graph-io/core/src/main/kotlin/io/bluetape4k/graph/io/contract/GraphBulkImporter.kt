package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

/** 공통 동기 벌크 임포터 계약. `S`는 포맷별 소스 타입. */
interface GraphBulkImporter<S : Any> {
    fun importGraph(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import java.io.Serializable

/** 정점 + 간선을 별도 파일로 구분하는 CSV 임포트 소스. */
data class CsvGraphImportSource(
    val vertices: GraphImportSource,
    val edges: GraphImportSource,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 정점 + 간선을 별도 파일로 구분하는 CSV 익스포트 싱크. */
data class CsvGraphExportSink(
    val vertices: GraphExportSink,
    val edges: GraphExportSink,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

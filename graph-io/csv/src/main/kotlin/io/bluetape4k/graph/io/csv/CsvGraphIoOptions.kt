package io.bluetape4k.graph.io.csv

import java.io.Serializable

/** CSV 형식별 I/O 옵션. 기본값은 `"prop."` 접두사 방식. */
data class CsvGraphIoOptions(
    val propertyMode: CsvPropertyMode = CsvPropertyMode.PrefixedColumns(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

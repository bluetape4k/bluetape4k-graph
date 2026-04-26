package io.bluetape4k.graph.io.csv

import java.io.Serializable

/**
 * CSV 형식별 I/O 옵션. 기본값은 `"prop."` 접두사 방식([CsvPropertyMode.PrefixedColumns]).
 *
 * ```kotlin
 * // 기본: 속성을 "prop." 접두사 컬럼으로 저장 → id, label, prop.name, prop.age
 * val defaults = CsvGraphIoOptions()
 *
 * // JSON 단일 컬럼: id, label, attributes
 * val jsonMode = CsvGraphIoOptions(propertyMode = CsvPropertyMode.RawJsonColumn("attributes"))
 *
 * // 속성 제외: id, label 만 저장
 * val noProps = CsvGraphIoOptions(propertyMode = CsvPropertyMode.None)
 * ```
 */
data class CsvGraphIoOptions(
    val propertyMode: CsvPropertyMode = CsvPropertyMode.PrefixedColumns(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

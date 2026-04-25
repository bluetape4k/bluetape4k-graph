package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord

/** CSV 컬럼 이름 생성 및 속성 추출을 담당하는 내부 헬퍼. */
internal class CsvRecordCodec(private val mode: CsvPropertyMode) {

    fun unionVertexHeader(records: Iterable<GraphIoVertexRecord>): List<String> {
        val reserved = listOf("id", "label")
        return unionHeader(reserved, records.asSequence().flatMap { it.properties.keys.asSequence() })
    }

    fun unionEdgeHeader(records: Iterable<GraphIoEdgeRecord>): List<String> {
        val reserved = listOf("id", "label", "from", "to")
        return unionHeader(reserved, records.asSequence().flatMap { it.properties.keys.asSequence() })
    }

    private fun unionHeader(reserved: List<String>, propertyKeys: Sequence<String>): List<String> {
        val propCols = propertyKeys.distinct().map { key -> propertyColumn(key) }.toSortedSet()
        val reservedSet = reserved.toSet()
        val collisions = propCols.filter { it in reservedSet }
        check(collisions.isEmpty()) { "Property column collides with reserved column: $collisions" }
        return reserved + propCols
    }

    fun propertyColumn(key: String): String = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> mode.prefix + key
        is CsvPropertyMode.RawJsonColumn -> mode.columnName
        CsvPropertyMode.None -> key
    }

    fun propertyKey(column: String): String = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> column.removePrefix(mode.prefix)
        else -> column
    }

    fun extractProperties(row: Map<String, String?>): Map<String, Any?> = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> row.entries
            .filter { it.key.startsWith(mode.prefix) && it.key !in RESERVED_ALL }
            .associate { it.key.removePrefix(mode.prefix) to it.value }

        is CsvPropertyMode.RawJsonColumn -> row[mode.columnName]
            ?.let { mapOf(mode.columnName to it) } ?: emptyMap()

        CsvPropertyMode.None -> emptyMap()
    }

    companion object {
        internal val RESERVED_VERTEX = listOf("id", "label")
        internal val RESERVED_EDGE = listOf("id", "label", "from", "to")
        internal val RESERVED_ALL = (RESERVED_VERTEX + RESERVED_EDGE).toSet()
    }
}

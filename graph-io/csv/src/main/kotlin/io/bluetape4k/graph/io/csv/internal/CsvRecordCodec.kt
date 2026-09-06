package io.bluetape4k.graph.io.csv.internal

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.type.TypeReference
import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.jackson.Jackson

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
        val columns = when (mode) {
            is CsvPropertyMode.RawJsonColumn -> sequenceOf(mode.columnName)
            else -> propertyKeys.map(::propertyColumn)
        }
        val propCols = columns.toSortedSet()
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

    /** GraphIoRecordSpool이 문자열로 정규화하기 전에 RawJson 값을 하나의 JSON payload로 고정한다. */
    fun prepareForSpool(properties: Map<String, Any?>): Map<String, Any?> = when (mode) {
        is CsvPropertyMode.RawJsonColumn -> {
            check(RAW_JSON_SPOOL_KEY !in properties) {
                "Property key '$RAW_JSON_SPOOL_KEY' is reserved by RawJsonColumn export"
            }
            mapOf(RAW_JSON_SPOOL_KEY to jsonMapper.writeValueAsString(properties))
        }
        is CsvPropertyMode.PrefixedColumns,
        CsvPropertyMode.None,
        -> properties
    }

    fun encodeProperty(column: String, properties: Map<String, Any?>): String = when (mode) {
        is CsvPropertyMode.RawJsonColumn -> properties[RAW_JSON_SPOOL_KEY]?.toString()
            ?: jsonMapper.writeValueAsString(properties)
        is CsvPropertyMode.PrefixedColumns,
        CsvPropertyMode.None,
        -> properties[propertyKey(column)]?.toString() ?: ""
    }

    fun extractProperties(row: Map<String, String?>): Map<String, Any?> = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> row.entries
            .filter { it.key.startsWith(mode.prefix) && it.key !in RESERVED_ALL }
            .associate { it.key.removePrefix(mode.prefix) to it.value }

        is CsvPropertyMode.RawJsonColumn -> row[mode.columnName]
            ?.let { decodeRawJson(it, mode.columnName) } ?: emptyMap()

        CsvPropertyMode.None -> emptyMap()
    }

    private fun decodeRawJson(raw: String, column: String): Map<String, Any?> {
        val node = try {
            jsonMapper.readTree(raw)
        } catch (cause: JacksonException) {
            throw IllegalArgumentException("Invalid JSON in RawJsonColumn '$column'", cause)
        }
        require(node?.isObject == true) {
            "RawJsonColumn '$column' must contain a JSON object"
        }
        return try {
            jsonMapper.convertValue(node, PROPERTY_MAP_TYPE)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("RawJsonColumn '$column' could not be converted to a property map", cause)
        }
    }

    companion object {
        private const val RAW_JSON_SPOOL_KEY = "\u0000bluetape4k.csv.rawJson"
        private val jsonMapper = Jackson.defaultJsonMapper
        private val PROPERTY_MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        internal val RESERVED_VERTEX = listOf("id", "label")
        internal val RESERVED_EDGE = listOf("id", "label", "from", "to")
        internal val RESERVED_ALL = (RESERVED_VERTEX + RESERVED_EDGE).toSet()
    }
}

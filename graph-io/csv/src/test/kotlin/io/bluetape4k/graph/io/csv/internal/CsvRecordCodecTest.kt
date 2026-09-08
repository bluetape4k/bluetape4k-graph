package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class CsvRecordCodecTest {

    companion object : KLogging()

    @Test
    fun `union header sorts property keys after reserved columns`() {
        val codec = CsvRecordCodec(CsvPropertyMode.PrefixedColumns())
        val recs = listOf(
            GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice")),
            GraphIoVertexRecord("v2", "Person", mapOf("age" to 30, "name" to "Bob")),
        )
        codec.unionVertexHeader(recs) shouldBeEqualTo listOf("id", "label", "prop.age", "prop.name")
    }

    @Test
    fun `None은 예약 이름을 포함한 모든 속성을 헤더에서 제외한다`() {
        val codec = CsvRecordCodec(CsvPropertyMode.None)
        val properties = mapOf("id" to "secret-id", "name" to "secret-name")

        codec.unionVertexHeader(listOf(GraphIoVertexRecord("v1", "L", properties))) shouldBeEqualTo
            listOf("id", "label")
        codec.unionEdgeHeader(listOf(GraphIoEdgeRecord("e1", "E", "v1", "v2", properties))) shouldBeEqualTo
            listOf("id", "label", "from", "to")
        codec.prepareForSpool(properties) shouldBeEqualTo emptyMap()
        codec.encodeProperty("name", properties) shouldBeEqualTo ""
    }

    @Test
    fun `raw json column is present even when all properties are empty`() {
        val codec = CsvRecordCodec(CsvPropertyMode.RawJsonColumn("attributes"))

        codec.unionVertexHeader(listOf(GraphIoVertexRecord("v1", "Person", emptyMap()))) shouldBeEqualTo
            listOf("id", "label", "attributes")
    }

    @Test
    fun `raw json properties preserve scalar null nested and escaped values`() {
        val codec = CsvRecordCodec(CsvPropertyMode.RawJsonColumn("attributes"))
        val expected = mapOf(
            "name" to "Alice",
            "age" to 30,
            "nullable" to null,
            "nested" to mapOf(
                "quote" to "a,\"b",
                "lines" to "one\ntwo",
            ),
            "items" to listOf(1, true),
        )

        val encoded = codec.encodeProperty("attributes", expected)

        codec.extractProperties(mapOf("attributes" to encoded)) shouldBeEqualTo expected
    }

    @Test
    fun `raw json empty properties are encoded as an empty object`() {
        val codec = CsvRecordCodec(CsvPropertyMode.RawJsonColumn("attributes"))

        codec.encodeProperty("attributes", emptyMap()) shouldBeEqualTo "{}"
        codec.extractProperties(mapOf("attributes" to "{}")) shouldBeEqualTo emptyMap()
    }

    @Test
    fun `raw json malformed or non object values fail explicitly`() {
        val codec = CsvRecordCodec(CsvPropertyMode.RawJsonColumn("attributes"))

        assertFailsWith<IllegalArgumentException> {
            codec.extractProperties(mapOf("attributes" to "{broken"))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.extractProperties(mapOf("attributes" to "[1, 2]"))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.extractProperties(mapOf("attributes" to "{} {}"))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.extractProperties(mapOf("attributes" to "{\"name\": \"Alice\",}"))
        }
    }
}

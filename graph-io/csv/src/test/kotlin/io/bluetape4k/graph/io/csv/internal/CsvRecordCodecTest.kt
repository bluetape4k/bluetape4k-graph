package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
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
    fun `prefixed column collision with reserved id fails`() {
        // prefix "x_" with property key "id" -> column "x_id", no collision.
        // Use CsvPropertyMode.None so the property key "id" maps directly to column "id" causing collision.
        val codec = CsvRecordCodec(CsvPropertyMode.None)
        val recs = listOf(GraphIoVertexRecord("v1", "L", mapOf("id" to "x")))

        assertFailsWith<IllegalStateException> {
            codec.unionVertexHeader(recs)
        }
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

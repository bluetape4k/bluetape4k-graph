package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CsvRecordCodecTest {

    companion object: KLogging()

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
}

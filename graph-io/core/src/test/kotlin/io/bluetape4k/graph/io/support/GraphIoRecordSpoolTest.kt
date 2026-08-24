package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import org.junit.jupiter.api.Test
import java.io.IOException

class GraphIoRecordSpoolTest {

    @Test
    fun `spool replays normalized records and preserves property key order`() {
        GraphIoRecordSpool().use { spool ->
            spool.appendVertices(
                listOf(
                    GraphIoVertexRecord("v-1", "Person", linkedMapOf("name" to "Alice", "age" to 30)),
                    GraphIoVertexRecord("v-2", "Person", linkedMapOf("name" to "Bob", "city" to null)),
                ),
            )
            spool.appendEdges(
                listOf(
                    GraphIoEdgeRecord("e-1", "KNOWS", "v-1", "v-2", linkedMapOf("since" to 2024)),
                ),
            )
            spool.finish()

            spool.vertexPropertyKeys.toList() shouldBeEqualTo listOf("name", "age", "city")
            spool.edgePropertyKeys.toList() shouldBeEqualTo listOf("since")
            spool.vertexRecords().toList() shouldBeEqualTo listOf(
                GraphIoVertexRecord("v-1", "Person", linkedMapOf("name" to "Alice", "age" to "30")),
                GraphIoVertexRecord("v-2", "Person", linkedMapOf("name" to "Bob", "city" to null)),
            )
            spool.vertexRecords().toList() shouldBeEqualTo spool.vertexRecords().toList()
            spool.edgeRecords().toList() shouldBeEqualTo listOf(
                GraphIoEdgeRecord("e-1", "KNOWS", "v-1", "v-2", linkedMapOf("since" to "2024")),
            )
        }
    }

    @Test
    fun `spool accepts empty input and requires finish before replay`() {
        GraphIoRecordSpool().use { spool ->
            spool.appendVertices(emptyList())
            spool.appendEdges(emptyList())
            assertFailsWith<IllegalStateException> { spool.vertexRecords().toList() }
            spool.finish()
            spool.vertexRecords().toList() shouldBeEqualTo emptyList()
            spool.edgeRecords().toList() shouldBeEqualTo emptyList()
        }
    }

    @Test
    fun `spool rejects writes after finish and replay after close`() {
        val spool = GraphIoRecordSpool()
        spool.finish()
        assertFailsWith<IllegalStateException> {
            spool.appendVertices(listOf(GraphIoVertexRecord("v-1", "Person")))
        }
        spool.close()
        assertFailsWith<IllegalStateException> { spool.vertexRecords().toList() }
        spool.close()
    }

    @Test
    fun `closing spool closes an abandoned replay input`() {
        val spool = GraphIoRecordSpool()
        spool.appendVertices(
            listOf(
                GraphIoVertexRecord("v-1", "Person"),
                GraphIoVertexRecord("v-2", "Person"),
            ),
        )
        spool.finish()

        val iterator = spool.vertexRecords().iterator()
        iterator.next().externalId shouldBeEqualTo "v-1"
        spool.close()

        assertFailsWith<IOException> { iterator.next() }
    }
}

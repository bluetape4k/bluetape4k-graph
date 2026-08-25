package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import org.junit.jupiter.api.Test
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

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

    @Test
    fun `spool writes payload without requesting a second byte array copy`() {
        val spool = GraphIoRecordSpool(
            maxRecordBytes = 256,
            payloadFactory = { NoCopyByteArrayOutputStream() },
        )

        spool.use {
            it.appendVertices(listOf(GraphIoVertexRecord("v-1", "Person", mapOf("name" to "Alice"))))
            it.finish()
            it.vertexRecords().toList().single().externalId shouldBeEqualTo "v-1"
        }
    }

    @Test
    fun `spool rejects an oversized payload before writing a partial record`() {
        GraphIoRecordSpool(maxRecordBytes = 32).use { spool ->
            val ex = assertFailsWith<IllegalArgumentException> {
                spool.appendVertices(
                    listOf(GraphIoVertexRecord("v-1", "Person", mapOf("payload" to "x".repeat(256)))),
                )
            }

            ex.message shouldContain "limit"
            spool.finish()
            spool.vertexRecords().toList() shouldBeEqualTo emptyList()
        }
    }

    @Test
    fun `spool constructor cleans resources when the second temp file fails`() {
        val createdFiles = mutableListOf<Path>()
        val outputs = mutableListOf<TrackingOutputStream>()
        var createCount = 0

        val ex = assertFailsWith<IOException> {
            GraphIoRecordSpool(
                maxRecordBytes = 256,
                createTempFile = { prefix, suffix ->
                    createCount++
                    if (createCount == 2) throw IOException("edge file creation failed")
                    Files.createTempFile(prefix, suffix).also(createdFiles::add)
                },
                openOutput = {
                    TrackingOutputStream().also(outputs::add).let(::DataOutputStream)
                },
            )
        }

        ex.message shouldBeEqualTo "edge file creation failed"
        outputs.single().closed.shouldBeTrue()
        createdFiles.single().let { Files.exists(it).shouldBeFalse() }
    }

    @Test
    fun `spool constructor cleans resources when the second output fails`() {
        val createdFiles = mutableListOf<Path>()
        val outputs = mutableListOf<TrackingOutputStream>()

        val ex = assertFailsWith<IOException> {
            GraphIoRecordSpool(
                maxRecordBytes = 256,
                createTempFile = { prefix, suffix ->
                    Files.createTempFile(prefix, suffix).also(createdFiles::add)
                },
                openOutput = {
                    if (outputs.isNotEmpty()) throw IOException("edge output open failed")
                    TrackingOutputStream().also(outputs::add).let(::DataOutputStream)
                },
            )
        }

        ex.message shouldBeEqualTo "edge output open failed"
        outputs.single().closed.shouldBeTrue()
        createdFiles.forEach { Files.exists(it).shouldBeFalse() }
    }

    private class NoCopyByteArrayOutputStream : java.io.ByteArrayOutputStream() {
        override fun toByteArray(): ByteArray = error("record payload must be written without copying")
    }

    private class TrackingOutputStream : OutputStream() {
        var closed = false

        override fun write(value: Int) = Unit

        override fun close() {
            closed = true
        }
    }
}

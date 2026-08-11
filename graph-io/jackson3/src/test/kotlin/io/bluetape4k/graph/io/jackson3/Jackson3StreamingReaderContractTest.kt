package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class Jackson3StreamingReaderContractTest {

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val source = sourceOf(
            """
            {"type":"vertex","id":"v1","label":"Person","properties":{"name":"Alice"}}
            {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
            {"type":"vertex","id":"v2","label":"Person","properties":{"name":"Bob"}}
            """.trimIndent(),
        )

        Jackson3NdJsonRecordFlowReader().readVertices(source).toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        Jackson3NdJsonRecordFlowReader().readEdges(
            sourceOf(
                """
                {"type":"vertex","id":"v1","label":"Person","properties":{}}
                {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
                """.trimIndent(),
            ),
        ).toList()
            .map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `caller owned stream remains open and owned stream closes`() = runSuspendIO {
        val callerOwned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toByteArray())
        val owned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toByteArray())

        Jackson3NdJsonRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(callerOwned),
        ).toList()
        Jackson3NdJsonRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(owned, closeInput = true),
        ).toList()

        callerOwned.closed.shouldBeFalse()
        owned.closed.shouldBeTrue()
        owned.closeCount shouldBeEqualTo 1
    }

    private fun sourceOf(content: String): GraphImportSource =
        GraphImportSource.InputStreamSource(content.byteInputStream())

    private class TrackingInputStream(content: ByteArray) : java.io.ByteArrayInputStream(content) {
        var closed: Boolean = false
            private set
        var closeCount: Int = 0
            private set

        override fun close() {
            closed = true
            closeCount++
            super.close()
        }
    }
}

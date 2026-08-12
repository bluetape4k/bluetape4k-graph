package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class Jackson3ReaderFailureTest {

    @Test
    fun `malformed json exposes safe line location`() = runSuspendIO {
        val source = GraphImportSource.InputStreamSource(
            """
            {"type":"vertex","id":"v1","properties":{}}
            {"type":"vertex","id":"secret-record","properties":{"payload":"secret-payload"}
            """.trimIndent().byteInputStream(),
        )

        val error = assertFailsWith<GraphIoReadException> {
            Jackson3NdJsonRecordFlowReader().readVertices(source).toList()
        }

        error.failure.location shouldBeEqualTo "line:2"
        error.message.orEmpty() shouldNotContain "secret-payload"
        error.message.orEmpty() shouldNotContain "secret-record"
    }

    @Test
    fun `malformed edge json reports edge phase`() = runSuspendIO {
        val source = GraphImportSource.InputStreamSource(
            """
            {"type":"edge","id":"e1","from":"v1","to":"v2","properties":{}}
            {"type":"edge","id":"secret-edge","from":"v1","to":
            """.trimIndent().byteInputStream(),
        )

        val error = assertFailsWith<GraphIoReadException> {
            Jackson3NdJsonRecordFlowReader().readEdges(source).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        error.failure.location shouldBeEqualTo "line:2"
        error.message.orEmpty() shouldNotContain "secret-edge"
    }
}

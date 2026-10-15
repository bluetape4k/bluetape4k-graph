package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class Jackson2ReaderFailureTest {

    companion object: KLogging()

    @Test
    fun `malformed json exposes safe line location`() = runSuspendIO {
        val source = """
            {"type":"vertex","id":"v1","properties":{}}
            {"type":"vertex","id":"secret-record","properties":{"payload":"secret-payload"}
            """
            .trimIndent()
            .byteInputStream()
            .asSource()

        val error = assertFailsWith<GraphIoReadException> {
            Jackson2NdJsonRecordFlowReader().readVertices(source).log("V").toList()
        }

        error.failure.location shouldBeEqualTo "line:2"
        error.message shouldNotContain "secret-payload"
        error.message shouldNotContain "secret-record"
    }

    @Test
    fun `malformed edge json reports edge phase`() = runSuspendIO {
        val source = """
            {"type":"edge","id":"e1","from":"v1","to":"v2","properties":{}}
            {"type":"edge","id":"secret-edge","from":"v1","to":
            """
            .trimIndent()
            .byteInputStream()
            .asSource()


        val error = assertFailsWith<GraphIoReadException> {
            Jackson2NdJsonRecordFlowReader().readEdges(source).log("E").toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        error.failure.location shouldBeEqualTo "line:2"
        error.message shouldNotContain "secret-edge"
    }
}

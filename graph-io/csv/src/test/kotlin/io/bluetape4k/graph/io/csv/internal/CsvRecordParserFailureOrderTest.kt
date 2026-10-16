package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.toUtf8Bytes
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class CsvRecordParserFailureOrderTest {

    companion object: KLogging()

    @Test
    fun `record mapper failure remains primary when owned source close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream("id,label\n,Person\n".toUtf8Bytes())
        val mappingFailure = GraphIoReadException(
            GraphIoFailure(
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.VERTICES,
                location = "row:1",
                message = "CSV vertex id is blank",
            ),
        )

        val thrown = assertFailsWith<GraphIoReadException> {
            CsvRecordParser().records(
                source = input.asSource(closeInput = true),
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.VERTICES,
                transform = { throw mappingFailure },
            ).toList()
        }

        log.debug { "thrown=$thrown" }
        thrown shouldBeSameInstanceAs mappingFailure
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("csv-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    private class CloseFailingInputStream(bytes: ByteArray): ByteArrayInputStream(bytes) {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("csv-close-failure")
        }
    }
}

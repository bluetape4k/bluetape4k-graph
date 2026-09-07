package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class CsvRecordParserFailureOrderTest {

    @Test
    fun `record mapper failure remains primary when owned source close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream("id,label\n,Person\n".toByteArray())
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
                source = GraphImportSource.InputStreamSource(input, closeInput = true),
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.VERTICES,
                transform = { throw mappingFailure },
            ).toList()
        }

        thrown shouldBeSameInstanceAs mappingFailure
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("csv-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    private class CloseFailingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("csv-close-failure")
        }
    }
}

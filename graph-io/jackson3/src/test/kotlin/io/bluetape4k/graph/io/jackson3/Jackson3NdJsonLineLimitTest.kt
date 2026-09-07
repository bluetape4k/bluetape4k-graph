package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.options.NdJsonReadOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class Jackson3NdJsonLineLimitTest {

    @Test
    fun `상한보다 하나 짧거나 같은 줄은 허용하고 하나 긴 줄은 거부한다`() = runSuspendIO {
        val exact = vertexLine(paddingSize = 16)
        val shorter = vertexLine(paddingSize = 15)
        val longer = vertexLine(paddingSize = 17)
        val reader = Jackson3NdJsonRecordFlowReader(NdJsonReadOptions(exact.length))

        reader.readVertices(sourceOf(shorter)).toList().single().externalId shouldBeEqualTo "v1"
        reader.readVertices(sourceOf(exact)).toList().single().externalId shouldBeEqualTo "v1"

        val error = assertFailsWith<GraphIoReadException> {
            reader.readVertices(sourceOf(longer)).toList()
        }
        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
        error.failure.fileRole shouldBeEqualTo GraphIoFileRole.UNIFIED
        error.failure.location shouldBeEqualTo "line:1"
        error.message.orEmpty().contains("padding").shouldBeFalse()

        val edgeError = assertFailsWith<GraphIoReadException> {
            reader.readEdges(sourceOf(longer)).toList()
        }
        edgeError.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        edgeError.failure.location shouldBeEqualTo "line:1"
    }

    @Test
    fun `CRLF CR과 Unicode 줄을 UTF-16 code unit 기준으로 읽는다`() = runSuspendIO {
        val first = vertexLine(id = "한글", paddingSize = 2)
        val second = vertexLine(id = "emoji-😀", paddingSize = 2)
        val third = vertexLine(id = "v3", paddingSize = 2)
        val limit = maxOf(first.length, second.length, third.length)
        val payload = "$first\r\n$second\r$third\n"

        val records = Jackson3NdJsonRecordFlowReader(NdJsonReadOptions(limit))
            .readVertices(sourceOf(payload))
            .toList()

        records.map { it.externalId } shouldBeEqualTo listOf("한글", "emoji-😀", "v3")
    }

    @Test
    fun `개행 없는 긴 입력은 전체 source를 소비하기 전에 실패한다`() = runSuspendIO {
        val payload = "{\"type\":\"vertex\",\"id\":\"secret-record\",\"padding\":\"" + "x".repeat(20_000)
        val input = CountingInputStream(payload.toByteArray())

        val error = assertFailsWith<GraphIoReadException> {
            Jackson3NdJsonRecordFlowReader(NdJsonReadOptions(maxLineChars = 64))
                .readVertices(GraphImportSource.InputStreamSource(input, closeInput = true))
                .toList()
        }

        (input.bytesRead < payload.toByteArray().size).shouldBeTrue()
        input.closed.shouldBeTrue()
        error.message.orEmpty().contains("secret-record").shouldBeFalse()
    }

    @Test
    fun `sync suspend virtual thread importer가 같은 줄 길이 상한을 적용한다`() = runSuspendIO {
        val payload = "{\"type\":\"vertex\",\"id\":\"secret-record\",\"padding\":\"" + "x".repeat(256)
        val readOptions = NdJsonReadOptions(maxLineChars = 64)

        val sync = Jackson3NdJsonBulkImporter(readOptions).importGraph(
            sourceOf(payload),
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
        val suspended = SuspendJackson3NdJsonBulkImporter(readOptions).importGraphSuspending(
            sourceOf(payload),
            TinkerGraphSuspendOperations(),
            GraphImportOptions(),
        )
        val virtual = Jackson3NdJsonVirtualThreadBulkImporter(readOptions).importGraphAsync(
            sourceOf(payload),
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).join()

        listOf(sync, suspended, virtual).forEach { report ->
            report.status shouldBeEqualTo GraphIoStatus.FAILED
            report.failures.single().location shouldBeEqualTo "line:1"
            report.failures.single().fileRole shouldBeEqualTo GraphIoFileRole.UNIFIED
            report.failures.single().message.contains("secret-record").shouldBeFalse()
        }
    }

    @Test
    fun `빈 줄만 계속되는 Flow도 취소를 관찰하고 owned source를 닫는다`() = runSuspendIO {
        coroutineScope {
            val input = EndlessBlankInputStream()
            val collecting = async {
                Jackson3NdJsonRecordFlowReader(NdJsonReadOptions(maxLineChars = 64))
                    .readVertices(GraphImportSource.InputStreamSource(input, closeInput = true))
                    .toList()
            }

            withTimeout(5_000) {
                while (input.readCount == 0) yield()
            }
            collecting.cancel()

            assertFailsWith<CancellationException> { collecting.await() }
            input.closeCount shouldBeEqualTo 1
        }
    }

    private fun sourceOf(content: String): GraphImportSource =
        GraphImportSource.InputStreamSource(content.byteInputStream())

    private fun vertexLine(id: String = "v1", paddingSize: Int): String =
        "{\"type\":\"vertex\",\"id\":\"$id\",\"properties\":{\"padding\":\"${"x".repeat(paddingSize)}\"}}"

    private class CountingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var bytesRead: Int = 0
        var closed: Boolean = false

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class EndlessBlankInputStream : InputStream() {
        @Volatile
        var readCount: Int = 0

        var closeCount: Int = 0

        override fun read(): Int {
            readCount++
            return '\n'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = length.coerceAtMost(64)
            buffer.fill('\n'.code.toByte(), offset, offset + count)
            readCount += count
            return count
        }

        override fun close() {
            closeCount++
        }
    }
}

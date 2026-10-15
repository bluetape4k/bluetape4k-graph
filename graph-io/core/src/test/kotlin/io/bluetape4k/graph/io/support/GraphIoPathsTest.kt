package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.source.toSink
import io.bluetape4k.graph.io.source.toSource
import io.bluetape4k.io.toInputStream
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.toUtf8Bytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class GraphIoPathsTest {

    companion object: KLogging()

    // ── openReader ───────────────────────────────────────────────────────────

    @Test
    fun `openReader honors path source`(@TempDir dir: Path) {
        val file = dir.resolve("a.txt").also { Files.writeString(it, "x\ny") }
        try {
            GraphImportSource.PathSource(file).openReader().use { r ->
                r.readLines().size shouldBeEqualTo 2
            }
        } finally {
            file.deleteIfExists().shouldBeTrue()
        }
    }

    @Test
    fun `openReader with inputStream source closeInput=true reads data`() {
        val bytes = "hello\nworld".toUtf8Bytes()
        val src = bytes.toInputStream().toSource(true)

        src.openReader().use { r ->
            r.readLines() shouldBeEqualTo listOf("hello", "world")
        }
    }

    @Test
    fun `openReader with inputStream source closeInput=false does not close underlying stream`() {
        var closed = false
        val underlying = object: ByteArrayInputStream("line1\nline2".toUtf8Bytes()) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val src = underlying.toSource(closeInput = false)
        val reader = GraphIoPaths.openReader(src)
        reader.readLine() shouldBeEqualTo "line1"
        reader.close()
        closed.shouldBeFalse()

        underlying.close()
        closed.shouldBeTrue()
    }

    // ── openWriter ───────────────────────────────────────────────────────────

    @Test
    fun `openWriter creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("nested/a.txt")
        nested.toSink().openWriter().use { it.write("hi") }

        Files.exists(nested).shouldBeTrue()
        Files.deleteIfExists(nested)
    }

    @Test
    fun `openWriter appends when PathSink append=true`(@TempDir dir: Path) {
        val file = dir.resolve("app.txt").also { Files.writeString(it, "first\n") }
        val sink = file.toSink(append = true)
        sink.openWriter().use { it.write("second") }
        Files.readString(file) shouldBeEqualTo "first\nsecond"
        file.deleteIfExists()
    }

    @Test
    fun `openWriter truncates when PathSink append=false`(@TempDir dir: Path) {
        val file = dir.resolve("trunc.txt").also { Files.writeString(it, "old content") }
        val sink = file.toSink(append = false)
        sink.openWriter().use { it.write("new") }
        Files.readString(file) shouldBeEqualTo "new"
    }

    @Test
    fun `openWriter with outputStream sink closeOutput=true writes data`() {
        val out = ByteArrayOutputStream()
        val sink = out.toSink(closeOutput = true)
        sink.openWriter().use { it.write("data") }
        out.toString() shouldBeEqualTo "data"
    }

    @Test
    fun `openWriter with outputStream sink closeOutput=false does not close underlying stream`() {
        val out = ByteArrayOutputStream()
        val sink = GraphExportSink.OutputStreamSink(out, closeOutput = false)
        val writer = GraphIoPaths.openWriter(sink)
        writer.write("test")
        writer.close()

        // stream still usable after writer.close() because closeOutput=false
        out.write("extra".toUtf8Bytes())
        out.toString() shouldBeEqualTo "testextra"
    }

    // ── openInputStream ──────────────────────────────────────────────────────

    @Test
    fun `openInputStream returns buffered stream for path source`(@TempDir dir: Path) {
        val file = dir.resolve("b.bin").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        file.toSource().openInputStream().use { stream ->
            stream.readBytes() shouldBeEqualTo byteArrayOf(1, 2, 3)
        }
    }

    @Test
    fun `openInputStream returns underlying stream for inputStream source closeInput=true`() {
        val data = byteArrayOf(10, 20, 30)
        data.toInputStream().toSource(closeInput = true).openInputStream().use { stream ->
            stream.readBytes() shouldBeEqualTo data
        }
    }

    @Test
    fun `openInputStream with closeInput=false does not close underlying stream`() {
        var closed = false
        val underlying = object: ByteArrayInputStream(byteArrayOf(7, 8, 9)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val src = underlying.toSource(closeInput = false)
        val stream = src.openInputStream()
        stream.read() // 7 읽기
        stream.close()
        closed.shouldBeFalse()

        // underlying stream에 여전히 접근 가능
        underlying.read() shouldBeEqualTo 8

        underlying.close()
        closed.shouldBeTrue()
    }

    // ── openOutputStream ─────────────────────────────────────────────────────

    @Test
    fun `openOutputStream creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("sub/dir/out.bin")
        nested.toSink(append = false).openOutputStream().use { stream ->
            stream.write(byteArrayOf(1))
        }
        Files.exists(nested).shouldBeTrue()
    }

    @Test
    fun `openOutputStream writes to path sink append=false`(@TempDir dir: Path) {
        val file = dir.resolve("out.bin").also { Files.write(it, byteArrayOf(99)) }
        file.toSink(append = false).openOutputStream().use { stream ->
            stream.write(byteArrayOf(1, 2))
        }
        Files.readAllBytes(file) shouldBeEqualTo byteArrayOf(1, 2)
    }

    @Test
    fun `openOutputStream appends to path sink append=true`(@TempDir dir: Path) {
        val file = dir.resolve("app.bin").also { Files.write(it, byteArrayOf(1)) }
        file.toSink(append = true).openOutputStream().use { stream ->
            stream.write(byteArrayOf(2, 3))
        }
        Files.readAllBytes(file) shouldBeEqualTo byteArrayOf(1, 2, 3)
    }

    @Test
    fun `openOutputStream with outputStream sink closeOutput=true writes data`() {
        val out = ByteArrayOutputStream()
        val sink = out.toSink(closeOutput = true)
        sink.openOutputStream().use { stream ->
            stream.write(byteArrayOf(5, 6))
        }
        out.toByteArray() shouldBeEqualTo byteArrayOf(5, 6)
    }

    @Test
    fun `openOutputStream with outputStream sink closeOutput=false flushes but keeps stream open`() {
        val out = ByteArrayOutputStream()
        val sink = out.toSink(closeOutput = false)
        val stream = sink.openOutputStream()
        stream.write(42)
        stream.close()

        // underlying stream still writable
        out.write(99)
        out.toByteArray() shouldBeEqualTo byteArrayOf(42, 99)
    }

    // ── describeSource ───────────────────────────────────────────────────────

    @Test
    fun `describeSource returns path string for path source`(@TempDir dir: Path) {
        val file = dir.resolve("x.txt")
        val src = file.toSource()
        src.describe().shouldNotBeNull()
        src.describe() shouldBeEqualTo file.toString()
    }

    @Test
    fun `describeSource returns null for inputStream source`() {
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(byteArrayOf()))
        GraphIoPaths.describeSource(src).shouldBeNull()
    }

    // ── byte accounting ──────────────────────────────────────────────────────

    @Test
    fun `sizeOf returns path file size and ignores streams`(@TempDir dir: Path) {
        val file = dir.resolve("bytes.bin").also { Files.write(it, byteArrayOf(1, 2, 3, 4)) }
        file.toSource().sizeOf() shouldBeEqualTo 4L
        file.toSink().sizeOf() shouldBeEqualTo 4L

        byteArrayOf().toInputStream().toSource().sizeOf().shouldBeNull()
        ByteArrayOutputStream().toSink().sizeOf().shouldBeNull()
    }

    @Test
    fun `sumSizes is null for unknown or overflowing input`() {
        GraphIoPaths.sumSizes(1L, null).shouldBeNull()
        GraphIoPaths.sumSizes(Long.MAX_VALUE, 1L).shouldBeNull()
        GraphIoPaths.sumSizes(2L, 3L, 5L) shouldBeEqualTo 10L
    }
}

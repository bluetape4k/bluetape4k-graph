package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class GraphIoPathsTest {

    // ── openReader ───────────────────────────────────────────────────────────

    @Test
    fun `openReader honors path source`(@TempDir dir: Path) {
        val file = dir.resolve("a.txt").also { Files.writeString(it, "x\ny") }
        GraphIoPaths.openReader(GraphImportSource.PathSource(file)).use { r ->
            r.readLines().size shouldBeEqualTo 2
        }
    }

    @Test
    fun `openReader with inputStream source closeInput=true reads data`() {
        val bytes = "hello\nworld".toByteArray()
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(bytes), closeInput = true)
        GraphIoPaths.openReader(src).use { r ->
            r.readLines() shouldBeEqualTo listOf("hello", "world")
        }
    }

    @Test
    fun `openReader with inputStream source closeInput=false does not close underlying stream`() {
        var closed = false
        val underlying = object : ByteArrayInputStream("line1\nline2".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        val src = GraphImportSource.InputStreamSource(underlying, closeInput = false)
        val reader = GraphIoPaths.openReader(src)
        reader.readLine() shouldBeEqualTo "line1"
        reader.close()
        closed shouldBeEqualTo false
    }

    // ── openWriter ───────────────────────────────────────────────────────────

    @Test
    fun `openWriter creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("nested/a.txt")
        GraphIoPaths.openWriter(GraphExportSink.PathSink(nested)).use { it.write("hi") }
        Files.exists(nested) shouldBeEqualTo true
    }

    @Test
    fun `openWriter appends when PathSink append=true`(@TempDir dir: Path) {
        val file = dir.resolve("app.txt").also { Files.writeString(it, "first\n") }
        val sink = GraphExportSink.PathSink(file, append = true)
        GraphIoPaths.openWriter(sink).use { it.write("second") }
        Files.readString(file) shouldBeEqualTo "first\nsecond"
    }

    @Test
    fun `openWriter truncates when PathSink append=false`(@TempDir dir: Path) {
        val file = dir.resolve("trunc.txt").also { Files.writeString(it, "old content") }
        val sink = GraphExportSink.PathSink(file, append = false)
        GraphIoPaths.openWriter(sink).use { it.write("new") }
        Files.readString(file) shouldBeEqualTo "new"
    }

    @Test
    fun `openWriter with outputStream sink closeOutput=true writes data`() {
        val out = ByteArrayOutputStream()
        val sink = GraphExportSink.OutputStreamSink(out, closeOutput = true)
        GraphIoPaths.openWriter(sink).use { it.write("data") }
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
        out.write("extra".toByteArray())
        out.toString() shouldBeEqualTo "testextra"
    }

    // ── openInputStream ──────────────────────────────────────────────────────

    @Test
    fun `openInputStream returns buffered stream for path source`(@TempDir dir: Path) {
        val file = dir.resolve("b.bin").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        GraphIoPaths.openInputStream(GraphImportSource.PathSource(file)).use { s ->
            s.readBytes() shouldBeEqualTo byteArrayOf(1, 2, 3)
        }
    }

    @Test
    fun `openInputStream returns underlying stream for inputStream source closeInput=true`() {
        val data = byteArrayOf(10, 20, 30)
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(data), closeInput = true)
        GraphIoPaths.openInputStream(src).use { s ->
            s.readBytes() shouldBeEqualTo data
        }
    }

    @Test
    fun `openInputStream with closeInput=false does not close underlying stream`() {
        var closed = false
        val underlying = object : ByteArrayInputStream(byteArrayOf(7, 8, 9)) {
            override fun close() { closed = true; super.close() }
        }
        val src = GraphImportSource.InputStreamSource(underlying, closeInput = false)
        val stream = GraphIoPaths.openInputStream(src)
        stream.read() // 7 읽기
        stream.close()
        closed shouldBeEqualTo false
        // underlying stream에 여전히 접근 가능
        underlying.read() shouldBeEqualTo 8
    }

    // ── openOutputStream ─────────────────────────────────────────────────────

    @Test
    fun `openOutputStream creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("sub/dir/out.bin")
        GraphIoPaths.openOutputStream(GraphExportSink.PathSink(nested, append = false)).use { s ->
            s.write(byteArrayOf(1))
        }
        Files.exists(nested) shouldBeEqualTo true
    }

    @Test
    fun `openOutputStream writes to path sink append=false`(@TempDir dir: Path) {
        val file = dir.resolve("out.bin").also { Files.write(it, byteArrayOf(99)) }
        GraphIoPaths.openOutputStream(GraphExportSink.PathSink(file, append = false)).use { s ->
            s.write(byteArrayOf(1, 2))
        }
        Files.readAllBytes(file) shouldBeEqualTo byteArrayOf(1, 2)
    }

    @Test
    fun `openOutputStream appends to path sink append=true`(@TempDir dir: Path) {
        val file = dir.resolve("app.bin").also { Files.write(it, byteArrayOf(1)) }
        GraphIoPaths.openOutputStream(GraphExportSink.PathSink(file, append = true)).use { s ->
            s.write(byteArrayOf(2, 3))
        }
        Files.readAllBytes(file) shouldBeEqualTo byteArrayOf(1, 2, 3)
    }

    @Test
    fun `openOutputStream with outputStream sink closeOutput=true writes data`() {
        val out = ByteArrayOutputStream()
        val sink = GraphExportSink.OutputStreamSink(out, closeOutput = true)
        GraphIoPaths.openOutputStream(sink).use { s -> s.write(byteArrayOf(5, 6)) }
        out.toByteArray() shouldBeEqualTo byteArrayOf(5, 6)
    }

    @Test
    fun `openOutputStream with outputStream sink closeOutput=false flushes but keeps stream open`() {
        val out = ByteArrayOutputStream()
        val sink = GraphExportSink.OutputStreamSink(out, closeOutput = false)
        val stream = GraphIoPaths.openOutputStream(sink)
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
        val src = GraphImportSource.PathSource(file)
        GraphIoPaths.describeSource(src).shouldNotBeNull()
        GraphIoPaths.describeSource(src) shouldBeEqualTo file.toString()
    }

    @Test
    fun `describeSource returns null for inputStream source`() {
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(byteArrayOf()))
        GraphIoPaths.describeSource(src).shouldBeNull()
    }
}

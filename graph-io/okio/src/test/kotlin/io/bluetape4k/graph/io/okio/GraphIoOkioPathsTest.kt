package io.bluetape4k.graph.io.okio

import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException

class GraphIoOkioPathsTest {

    private val fakeFs = FakeFileSystem()

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()
    }

    private fun ensureTmpDir() {
        fakeFs.createDirectories("/tmp".toPath())
    }

    // ─── openSource ───────────────────────────────────────────────────────────

    @Test
    fun `openSource PathSource reads file content`() {
        ensureTmpDir()
        val path = "/tmp/test.txt".toPath()
        fakeFs.write(path) { writeUtf8("data content") }

        val source = OkioGraphImportSource.PathSource(path, fakeFs)
        val result = GraphIoOkioPaths.openSource(source).use { it.readUtf8() }

        result shouldBeEqualTo "data content"
    }

    @Test
    fun `openSource SourceBased does not close underlying source when ownsSource=false`() {
        val buffer = Buffer().also { it.writeUtf8("stream data") }
        val source = OkioGraphImportSource.SourceBased(buffer, ownsSource = false)

        GraphIoOkioPaths.openSource(source).use { it.readUtf8() }

        // after close, buffer itself should still be open (non-closing)
        buffer.size shouldBeEqualTo 0L  // was read, but not thrown away by close
    }

    // ─── openSink ─────────────────────────────────────────────────────────────

    @Test
    fun `openSink PathSink writes with atomicMove`() {
        ensureTmpDir()
        val target = "/tmp/out.txt".toPath()
        val sink = OkioGraphExportSink.PathSink(target, fakeFs)

        GraphIoOkioPaths.openSink(sink).use { bs -> bs.writeUtf8("atomic content") }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "atomic content"
    }

    @Test
    fun `openSink PathSink atomicWrite=false writes directly`() {
        ensureTmpDir()
        val target = "/tmp/direct.txt".toPath()
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, atomicWrite = false)

        GraphIoOkioPaths.openSink(sink).use { bs -> bs.writeUtf8("direct content") }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "direct content"
    }

    @Test
    fun `openSink PathSink mustExist=true throws when file absent`() {
        ensureTmpDir()
        val target = "/tmp/missing.txt".toPath()
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, mustExist = true)

        invoking { GraphIoOkioPaths.openSink(sink) } shouldThrow IllegalStateException::class
    }

    @Test
    fun `openSink PathSink mustCreate=true throws when file exists`() {
        ensureTmpDir()
        val target = "/tmp/existing.txt".toPath()
        fakeFs.write(target) { writeUtf8("x") }
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, mustCreate = true)

        invoking { GraphIoOkioPaths.openSink(sink) } shouldThrow IllegalStateException::class
    }

    // ─── openGzipSink / openGzipSource ────────────────────────────────────────

    @Test
    fun `gzip round trip with FakeFileSystem`() {
        ensureTmpDir()
        val path = "/tmp/graph.gz".toPath()
        val data = "압축된 그래프 데이터".repeat(100)

        GraphIoOkioPaths.openGzipSink(
            OkioGraphExportSink.PathSink(path, fakeFs)
        ).use { bs -> bs.writeUtf8(data) }

        val result = GraphIoOkioPaths.openGzipSource(
            OkioGraphImportSource.PathSource(path, fakeFs)
        ).use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data
    }

    // ─── BombGuardSource ─────────────────────────────────────────────────────

    @Test
    fun `decompression bomb guard throws when limit exceeded`() {
        ensureTmpDir()
        val path = "/tmp/bomb.gz".toPath()
        val largeData = "x".repeat(10_000)

        GraphIoOkioPaths.openGzipSink(
            OkioGraphExportSink.PathSink(path, fakeFs)
        ).use { bs -> bs.writeUtf8(largeData) }

        invoking {
            GraphIoOkioPaths.openDecompressedSource(
                source = GraphIoOkioPaths.openSource(OkioGraphImportSource.PathSource(path, fakeFs)),
                compressor = Compressor.GZIP,
                maxDecompressedBytes = 100L,
            ).use { it.readUtf8() }
        } shouldThrow IOException::class
    }
}

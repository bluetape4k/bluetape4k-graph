package io.bluetape4k.graph.io.okio

import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.tink.daead.TinkDaeads
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException

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
    fun `sizeOf reports path bytes and CSV paired bytes`() {
        ensureTmpDir()
        val path = "/tmp/graph.ndjson".toPath()
        fakeFs.write(path) { writeUtf8("12345") }
        GraphIoOkioPaths.sizeOf(OkioGraphImportSource.PathSource(path, fakeFs)) shouldBeEqualTo 5L

        val stem = "/tmp/paired".toPath()
        fakeFs.write("/tmp/paired_vertices.csv".toPath()) { writeUtf8("123") }
        fakeFs.write("/tmp/paired_edges.csv".toPath()) { writeUtf8("4567") }
        GraphIoOkioPaths.sizeOfCsv(OkioGraphImportSource.PathSource(stem, fakeFs)) shouldBeEqualTo 7L
        GraphIoOkioPaths.sizeOf(OkioGraphImportSource.SourceBased(Buffer())) shouldBeEqualTo null
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

        assertFailsWith<IllegalStateException> {
            GraphIoOkioPaths.openSink(sink)
        }
    }

    @Test
    fun `openSink PathSink mustCreate=true throws when file exists`() {
        ensureTmpDir()
        val target = "/tmp/existing.txt".toPath()
        fakeFs.write(target) { writeUtf8("x") }
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, mustCreate = true)

        assertFailsWith<IllegalStateException> {
            GraphIoOkioPaths.openSink(sink)
        }
    }

    @Test
    fun `openSink PathSink createParentDirectories=true creates missing parent`() {
        val target = "/new/nested/dir/out.txt".toPath()
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, createParentDirectories = true)

        GraphIoOkioPaths.openSink(sink).use { bs -> bs.writeUtf8("nested write") }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "nested write"
    }

    @Test
    fun `openSink PathSink createParentDirectories=false throws when parent missing`() {
        val target = "/nonexistent/out.txt".toPath()
        val sink = OkioGraphExportSink.PathSink(target, fakeFs, createParentDirectories = false)

        assertFailsWith<Exception> {
            GraphIoOkioPaths.openSink(sink)
        }
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

    // ─── DAEAD chunk encryption ─────────────────────────────────────────────

    @Test
    fun `DAEAD chunk round trip with FakeFileSystem`() {
        ensureTmpDir()
        val path = "/tmp/graph.enc".toPath()
        val associatedData = "graph-okio".encodeToByteArray()
        val data = "encrypted graph data\n".repeat(100)

        GraphIoOkioPaths.openDaeadEncryptedSink(
            sink = OkioGraphExportSink.PathSink(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = associatedData,
        ).use { bs -> bs.writeUtf8(data) }

        val ciphertext = fakeFs.read(path) { readByteArray() }
        (ciphertext.contentEquals(data.encodeToByteArray())) shouldBeEqualTo false

        val result = GraphIoOkioPaths.openDaeadDecryptedSource(
            source = OkioGraphImportSource.PathSource(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = associatedData,
        ).use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data
    }

    @Test
    fun `atomicWrite DAEAD setup failure leaves target unchanged and deletes tmp`() {
        ensureTmpDir()
        val path = "/tmp/graph-setup-fail.enc".toPath()
        fakeFs.write(path) { writeUtf8("ORIGINAL") }

        assertFailsWith<IllegalArgumentException> {
            GraphIoOkioPaths.openDaeadEncryptedSink(
                sink = OkioGraphExportSink.PathSink(path, fakeFs, atomicWrite = true),
                daead = TinkDaeads.AES256_SIV,
                chunkSize = 0,
            )
        }

        fakeFs.read(path) { readUtf8() } shouldBeEqualTo "ORIGINAL"
        tempFilesFor(path) shouldBeEqualTo emptyList()
    }

    @Test
    fun `gzip DAEAD chunk round trip with FakeFileSystem`() {
        ensureTmpDir()
        val path = "/tmp/graph.ndjson.gz.enc".toPath()
        val associatedData = "gzip-daead".encodeToByteArray()
        val data = """{"id":1,"label":"Person"}""" + "\n"

        GraphIoOkioPaths.openGzipDaeadEncryptedSink(
            sink = OkioGraphExportSink.PathSink(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = associatedData,
        ).use { bs -> bs.writeUtf8(data.repeat(100)) }

        val result = GraphIoOkioPaths.openDaeadDecryptedGzipSource(
            source = OkioGraphImportSource.PathSource(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = associatedData,
        ).use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data.repeat(100)
    }

    @Test
    fun `atomicWrite gzip DAEAD setup failure leaves target unchanged and deletes tmp`() {
        ensureTmpDir()
        val path = "/tmp/graph-setup-fail.ndjson.gz.enc".toPath()
        fakeFs.write(path) { writeUtf8("ORIGINAL") }

        assertFailsWith<IllegalArgumentException> {
            GraphIoOkioPaths.openGzipDaeadEncryptedSink(
                sink = OkioGraphExportSink.PathSink(path, fakeFs, atomicWrite = true),
                daead = TinkDaeads.AES256_SIV,
                chunkSize = 0,
            )
        }

        fakeFs.read(path) { readUtf8() } shouldBeEqualTo "ORIGINAL"
        tempFilesFor(path) shouldBeEqualTo emptyList()
    }

    @Test
    fun `DAEAD decryption fails with wrong associated data`() {
        ensureTmpDir()
        val path = "/tmp/graph-wrong-ad.enc".toPath()

        GraphIoOkioPaths.openDaeadEncryptedSink(
            sink = OkioGraphExportSink.PathSink(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = "right".encodeToByteArray(),
        ).use { bs -> bs.writeUtf8("secret") }

        assertFailsWith<GeneralSecurityException> {
            GraphIoOkioPaths.openDaeadDecryptedSource(
                source = OkioGraphImportSource.PathSource(path, fakeFs),
                daead = TinkDaeads.AES256_SIV,
                associatedData = "wrong".encodeToByteArray(),
            ).use { bs -> bs.readUtf8() }
        }
    }

    @Test
    fun `DAEAD decryption fails with truncated ciphertext`() {
        ensureTmpDir()
        val path = "/tmp/graph-truncated.enc".toPath()
        val associatedData = "truncate-check".encodeToByteArray()

        GraphIoOkioPaths.openDaeadEncryptedSink(
            sink = OkioGraphExportSink.PathSink(path, fakeFs),
            daead = TinkDaeads.AES256_SIV,
            associatedData = associatedData,
        ).use { bs -> bs.writeUtf8("secret payload") }

        val ciphertext = fakeFs.read(path) { readByteArray() }
        fakeFs.write(path) {
            write(ciphertext, offset = 0, byteCount = ciphertext.size - 1)
        }

        assertFailsWith<EOFException> {
            GraphIoOkioPaths.openDaeadDecryptedSource(
                source = OkioGraphImportSource.PathSource(path, fakeFs),
                daead = TinkDaeads.AES256_SIV,
                associatedData = associatedData,
            ).use { bs -> bs.readUtf8() }
        }
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

        assertFailsWith<IOException> {
            GraphIoOkioPaths.openDecompressedSource(
                source = GraphIoOkioPaths.openSource(OkioGraphImportSource.PathSource(path, fakeFs)),
                compressor = Compressor.GZIP,
                maxDecompressedBytes = 100L,
            ).use { it.readUtf8() }
        }
    }

    @Test
    fun `BombGuardSource allows reads up to maxBytes exactly without throwing`() {
        ensureTmpDir()
        val data = "x".repeat(500)  // exactly 500 bytes
        val path = "/tmp/exact.gz".toPath()

        GraphIoOkioPaths.openGzipSink(
            OkioGraphExportSink.PathSink(path, fakeFs)
        ).use { bs -> bs.writeUtf8(data) }

        // Should succeed with limit == data size
        val result = GraphIoOkioPaths.openDecompressedSource(
            source = GraphIoOkioPaths.openSource(OkioGraphImportSource.PathSource(path, fakeFs)),
            compressor = Compressor.GZIP,
            maxDecompressedBytes = 500L,
        ).use { it.readUtf8() }

        result shouldBeEqualTo data
    }

    private fun tempFilesFor(path: okio.Path): List<okio.Path> =
        fakeFs.list(path.parent ?: "/".toPath())
            .filter { it.segments.last().startsWith("${path.segments.last()}.tmp.") }
}

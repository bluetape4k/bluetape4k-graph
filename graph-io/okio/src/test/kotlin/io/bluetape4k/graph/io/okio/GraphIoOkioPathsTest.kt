package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotContentEqual
import io.bluetape4k.graph.io.support.sizeOf
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.tink.daead.TinkDaeads
import okio.Buffer
import okio.Path.Companion.toPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException

class GraphIoOkioPathsTest: AbstractOkioTest() {

    companion object: KLogging()

    @BeforeEach
    fun beforeEach() {
        fakeFs.createDirectories("/tmp".toPath())
    }

    // ─── openSource ───────────────────────────────────────────────────────────

    @Test
    fun `openSource PathSource reads file content`() {
        val path = "/tmp/test.txt".toPath()
        fakeFs.write(path) { writeUtf8("data content") }

        val source = path.asOkioSource(fakeFs)
        val result = source.openSource().use { it.readUtf8() }

        result shouldBeEqualTo "data content"
    }

    @Test
    fun `sizeOf reports path bytes and CSV paired bytes`() {

        val path = "/tmp/graph.ndjson".toPath()
        fakeFs.write(path) { writeUtf8("12345") }

        path.asOkioSource(fakeFs).sizeOf() shouldBeEqualTo 5L

        val stem = "/tmp/paired".toPath()
        fakeFs.write("/tmp/paired_vertices.csv".toPath()) { writeUtf8("123") }
        fakeFs.write("/tmp/paired_edges.csv".toPath()) { writeUtf8("4567") }
        stem.asOkioSource(fakeFs).sizeOfCsv() shouldBeEqualTo 7L
        Buffer().asSource().sizeOf().shouldBeNull()
    }

    @Test
    fun `openSource SourceBased does not close underlying source when ownsSource=false`() {
        val buffer = Buffer().also { it.writeUtf8("stream data") }
        val source = buffer.asOkioSource(ownsSource = false)

        source.openSource().use { it.readUtf8() }

        // after close, buffer itself should still be open (non-closing)
        buffer.size shouldBeEqualTo 0L  // was read, but not thrown away by close
    }

    // ─── openSink ─────────────────────────────────────────────────────────────

    @Test
    fun `openSink PathSink writes with atomicMove`() {

        val target = "/tmp/out.txt".toPath()
        val sink = target.asOkioSink(fileSystem = fakeFs)

        sink.openSink().use { bs ->
            bs.writeUtf8("atomic content")
        }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "atomic content"
    }

    @Test
    fun `openSink PathSink atomicWrite=false writes directly`() {

        val target = "/tmp/direct.txt".toPath()
        val sink = target.asOkioSink(fileSystem = fakeFs, atomicWrite = false)

        sink.openSink().use { bs ->
            bs.writeUtf8("direct content")
        }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "direct content"
    }

    @Test
    fun `openSink PathSink mustExist=true throws when file absent`() {

        val target = "/tmp/missing.txt".toPath()
        val sink = target.asOkioSink(fileSystem = fakeFs, mustExist = true)

        assertFailsWith<IllegalStateException> {
            sink.openSink()
        }
    }

    @Test
    fun `openSink PathSink mustCreate=true throws when file exists`() {
        val target = "/tmp/existing.txt".toPath()
        fakeFs.write(target) { writeUtf8("x") }
        val sink = target.asOkioSink(fileSystem = fakeFs, mustCreate = true)

        assertFailsWith<IllegalStateException> {
            GraphIoOkioPaths.openSink(sink)
        }
        assertFailsWith<IllegalStateException> {
            sink.openSink()
        }
    }

    @Test
    fun `openSink PathSink createParentDirectories=true creates missing parent`() {
        val target = "/new/nested/dir/out.txt".toPath()
        val sink = target.asOkioSink(fileSystem = fakeFs, createParentDirectories = true)

        sink.openSink().use { bs -> bs.writeUtf8("nested write") }

        val result = fakeFs.read(target) { readUtf8() }
        result shouldBeEqualTo "nested write"
    }

    @Test
    fun `openSink PathSink createParentDirectories=false throws when parent missing`() {
        val target = "/nonexistent/out.txt".toPath()
        val sink = target.asOkioSink(fileSystem = fakeFs, createParentDirectories = false)

        assertFailsWith<Exception> {
            GraphIoOkioPaths.openSink(sink)
        }
    }

    // ─── openGzipSink / openGzipSource ────────────────────────────────────────

    @Test
    fun `gzip round trip with FakeFileSystem`() {
        val path = "/tmp/graph.gz".toPath()
        val data = "압축된 그래프 데이터".repeat(100)

        path.asOkioSink(fileSystem = fakeFs).openGzipSink().use { bs ->
            bs.writeUtf8(data)
        }

        val result = path.asOkioSource(fakeFs).openGzipSource().use { bs ->
            bs.readUtf8()
        }

        result shouldBeEqualTo data
    }

    // ─── DAEAD chunk encryption ─────────────────────────────────────────────

    @Test
    fun `DAEAD chunk round trip with FakeFileSystem`() {
        val path = "/tmp/graph.enc".toPath()
        val associatedData = "graph-okio".encodeToByteArray()
        val data = "encrypted graph data\n".repeat(100)

        path.asOkioSink(fakeFs)
            .openDaeadEncryptedSink(daead = TinkDaeads.AES256_SIV, associatedData = associatedData)
            .use { bs -> bs.writeUtf8(data) }

        // 암호화 되었으므로 평문과 다르다.
        val cipherText = fakeFs.read(path) { readByteArray() }
        cipherText shouldNotContentEqual data.encodeToByteArray()

        // 파일 내용을 읽어 복호화한다 
        val result = path.asOkioSource(fakeFs)
            .openDaeadDecryptedSource(TinkDaeads.AES256_SIV, associatedData = associatedData)
            .use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data
    }

    @Test
    fun `atomicWrite DAEAD setup failure leaves target unchanged and deletes tmp`() {
        val path = "/tmp/graph-setup-fail.enc".toPath()
        fakeFs.write(path) { writeUtf8("ORIGINAL") }

        // chunkSize = 0 면 안된다
        assertFailsWith<IllegalArgumentException> {
            path.asOkioSink(fileSystem = fakeFs, createParentDirectories = true)
                .openDaeadEncryptedSink(TinkDaeads.AES256_SIV, chunkSize = 0)
        }

        fakeFs.read(path) { readUtf8() } shouldBeEqualTo "ORIGINAL"
        tempFilesFor(path) shouldBeEqualTo emptyList()
    }

    @Test
    fun `gzip DAEAD chunk round trip with FakeFileSystem`() {
        val path = "/tmp/graph.ndjson.gz.enc".toPath()
        val associatedData = "gzip-daead".encodeToByteArray()
        val data = """{"id":1,"label":"사람"}""" + "\n".repeat(100)

        log.debug { "Gzip + DAEAD 변환으로 파일 저장과 읽기" }
        path.asOkioSink(fakeFs)
            .openGzipDaeadEncryptedSink(TinkDaeads.AES256_SIV, associatedData = associatedData)
            .use { bs -> bs.writeUtf8(data) }

        val result = path.asOkioSource(fakeFs)
            .openDaeadDecryptedGzipSource(TinkDaeads.AES256_SIV, associatedData = associatedData)
            .use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data
    }

    @Test
    fun `atomicWrite gzip DAEAD setup failure leaves target unchanged and deletes tmp`() {
        val path = "/tmp/graph-setup-fail.ndjson.gz.enc".toPath()
        fakeFs.write(path) { writeUtf8("ORIGINAL") }

        assertFailsWith<IllegalArgumentException> {
            path.asOkioSink(fakeFs, atomicWrite = true)
                .openGzipDaeadEncryptedSink(TinkDaeads.AES256_SIV, 0)
                .use { bs -> bs.writeUtf8("openGzipDaeadEncryptedSink 에서 예외가 발생합니다.") }
        }

        fakeFs.read(path) { readUtf8() } shouldBeEqualTo "ORIGINAL"
        tempFilesFor(path) shouldBeEqualTo emptyList()
    }

    @Test
    fun `DAEAD decryption fails with wrong associated data`() {
        val path = "/tmp/graph-wrong-ad.enc".toPath()

        path.asOkioSink(fakeFs)
            .openDaeadEncryptedSink(TinkDaeads.AES256_SIV, associatedData = "right".toUtf8Bytes())
            .use { bs -> bs.writeUtf8("secret") }

        assertFailsWith<GeneralSecurityException> {
            path.asOkioSource(fakeFs)
                .openDaeadDecryptedSource(TinkDaeads.AES256_SIV, "wrong".toUtf8Bytes())
                .use { bs -> bs.readUtf8() }
        }
    }

    @Test
    fun `DAEAD decryption fails with truncated ciphertext`() {

        val path = "/tmp/graph-truncated.enc".toPath()
        val associatedData = "truncate-check".toUtf8Bytes()

        path.asOkioSink(fakeFs)
            .openDaeadEncryptedSink(TinkDaeads.AES256_SIV, associatedData = associatedData)
            .use { bs -> bs.writeUtf8("secret payload") }

        val ciphertext = fakeFs.read(path) { readByteArray() }
        fakeFs.write(path) {
            write(ciphertext, offset = 0, byteCount = ciphertext.size - 1)
        }

        assertFailsWith<EOFException> {
            path.asOkioSource(fakeFs)
                .openDaeadDecryptedSource(TinkDaeads.AES256_SIV, associatedData = associatedData)
                .use { bs -> bs.readUtf8() }
        }
    }

    // ─── BombGuardSource ─────────────────────────────────────────────────────

    @Test
    fun `decompression bomb guard throws when limit exceeded`() {

        val path = "/tmp/bomb.gz".toPath()
        val largeData = "x".repeat(10_000)

        GraphIoOkioPaths.openGzipSink(
            OkioGraphExportSink.PathSink(path, fakeFs)
        ).use { bs -> bs.writeUtf8(largeData) }

        path.asOkioSink(fakeFs)
            .openGzipSink()
            .use { bs -> bs.writeUtf8(largeData) }

        assertFailsWith<IOException> {
            path.asOkioSource(fakeFs)
                .openDecompressedSource(Compressor.GZIP, maxDecompressedBytes = 100L)
                .use { bs -> bs.readUtf8() }
        }
    }

    @Test
    fun `BombGuardSource allows reads up to maxBytes exactly without throwing`() {

        val data = "x".repeat(500)  // exactly 500 bytes
        val path = "/tmp/exact.gz".toPath()

        path.asOkioSink(fakeFs)
            .openGzipSink()
            .use { bs -> bs.writeUtf8(data) }

        // Should succeed with limit == data size
        val result = path.asOkioSource(fakeFs)
            .openDecompressedSource(Compressor.GZIP, maxDecompressedBytes = 500L)
            .use { bs -> bs.readUtf8() }

        result shouldBeEqualTo data
    }

    private fun tempFilesFor(path: okio.Path): List<okio.Path> =
        fakeFs.list(path.parent ?: "/".toPath())
            .filter { it.segments.last().startsWith("${path.segments.last()}.tmp.") }
}

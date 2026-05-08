package io.bluetape4k.graph.io.okio.bridge

import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import okio.Buffer
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import okio.sink
import okio.source
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BridgesTest {

    private val fakeFs = FakeFileSystem()

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()
    }

    @Test
    fun `toInputStream reads all bytes`() {
        val data = "hello okio".toByteArray()
        val bs = Buffer().also { it.write(data) }.buffer()
        val bytes = bs.toInputStream().use { it.readBytes() }
        bytes shouldBeEqualTo data
    }

    @Test
    fun `toOutputStream writes bytes`() {
        val baos = ByteArrayOutputStream()
        val bs = baos.sink().buffer()
        bs.toOutputStream().write("world".toByteArray())
        bs.flush()
        baos.toByteArray() shouldBeEqualTo "world".toByteArray()
    }

    @Test
    fun `asClosingOutputStream writes and closes underlying sink`() {
        val baos = ByteArrayOutputStream()
        val bs = baos.sink().buffer()
        val os = bs.asClosingOutputStream()
        os.write("close chain".toByteArray())
        os.close()
        baos.toByteArray() shouldBeEqualTo "close chain".toByteArray()
    }

    @Test
    fun `asClosingOutputStream write single byte`() {
        val baos = ByteArrayOutputStream()
        val bs = baos.sink().buffer()
        val os = bs.asClosingOutputStream()
        os.write(0x41) // 'A'
        os.close()
        baos.toByteArray() shouldBeEqualTo byteArrayOf(0x41)
    }

    @Test
    fun `toReader reads utf8`() {
        val text = "안녕하세요"
        val bs = Buffer().also { it.writeUtf8(text) }.buffer()
        val result = bs.toReader().use { it.readText() }
        result shouldBeEqualTo text
    }

    @Test
    fun `toWriter writes utf8`() {
        val baos = ByteArrayOutputStream()
        val bs = baos.sink().buffer()
        // toWriter returns outputStream().writer() — closing the writer closes the underlying sink
        val writer = bs.toWriter()
        writer.write("테스트")
        writer.flush()
        bs.flush()
        writer.close()
        val result = String(baos.toByteArray(), Charsets.UTF_8)
        result shouldBeEqualTo "테스트"
    }

    @Test
    fun `toReader uses provided charset for non-UTF8 decoding`() {
        val text = "Hello charset"
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val bs = Buffer().also { it.write(bytes) }.buffer()
        val result = bs.toReader(Charsets.ISO_8859_1).use { it.readText() }
        result shouldBeEqualTo text
    }

    @Test
    fun `toWriter writes bytes encoded with provided non-UTF8 charset`() {
        val baos = ByteArrayOutputStream()
        val bs = baos.sink().buffer()
        val writer = bs.toWriter(Charsets.ISO_8859_1)
        writer.write("Latin text")
        writer.flush()
        bs.flush()
        writer.close()
        val result = String(baos.toByteArray(), Charsets.ISO_8859_1)
        result shouldBeEqualTo "Latin text"
    }

    @Test
    fun `writeAsOutputStream writes bytes through OkIO sink`() {
        val path = "/write-as-os.bin".toPath()
        val expected = "writeAsOutputStream test".toByteArray()
        writeAsOutputStream(OkioGraphExportSink.PathSink(path, fakeFs)) { os ->
            os.write(expected)
        }
        fakeFs.read(path) { readByteArray() } shouldBeEqualTo expected
    }

    @Test
    fun `readAsInputStream reads bytes through OkIO source`() {
        val path = "/read-as-is.bin".toPath()
        val expected = "readAsInputStream test".toByteArray()
        fakeFs.write(path) { write(expected) }

        var read: ByteArray = byteArrayOf()
        readAsInputStream(OkioGraphImportSource.PathSource(path, fakeFs)) { is_ ->
            read = is_.readBytes()
        }
        read shouldBeEqualTo expected
    }

    @Test
    fun `okioWriteTo provides both BufferedSink and OutputStream`() {
        val path = "/okio-write.bin".toPath()
        val expected = "okioWriteTo".toByteArray()
        okioWriteTo(OkioGraphExportSink.PathSink(path, fakeFs)) { _, os ->
            os.write(expected)
        }
        fakeFs.read(path) { readByteArray() } shouldBeEqualTo expected
    }

    @Test
    fun `okioReadFrom provides both BufferedSource and InputStream`() {
        val path = "/okio-read.bin".toPath()
        val expected = "okioReadFrom".toByteArray()
        fakeFs.write(path) { write(expected) }

        var read: ByteArray = byteArrayOf()
        okioReadFrom(OkioGraphImportSource.PathSource(path, fakeFs)) { _, is_ ->
            read = is_.readBytes()
        }
        read shouldBeEqualTo expected
    }
}

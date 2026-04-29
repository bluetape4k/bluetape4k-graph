package io.bluetape4k.graph.io.okio.bridge

import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BridgesTest {

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
}

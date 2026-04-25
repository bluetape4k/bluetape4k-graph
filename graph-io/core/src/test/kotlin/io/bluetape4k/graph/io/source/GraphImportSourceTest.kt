package io.bluetape4k.graph.io.source

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class GraphImportSourceTest {

    @Test
    fun `path source defaults to UTF-8`() {
        val src = GraphImportSource.PathSource(Path.of("in.csv"))
        src.charset shouldBeEqualTo Charsets.UTF_8
    }

    @Test
    fun `input stream source defaults to caller-owned close`() {
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(ByteArray(0)))
        src.closeInput shouldBeEqualTo false
    }
}

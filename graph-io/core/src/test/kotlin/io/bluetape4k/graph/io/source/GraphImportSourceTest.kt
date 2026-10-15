package io.bluetape4k.graph.io.source

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class GraphImportSourceTest {

    companion object: KLogging()

    @Test
    fun `path source defaults to UTF-8`() {
        val src = Path.of("in.csv").asSource()

        log.debug { "src=$src" }
        src.charset shouldBeEqualTo Charsets.UTF_8
    }

    @Test
    fun `input stream source defaults to caller-owned close`() {
        val src = ByteArrayInputStream(ByteArray(0)).asSource()

        log.debug { "src=$src" }
        src.closeInput.shouldBeFalse()
    }
}

package io.bluetape4k.graph.io.source

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class GraphExportSinkTest {

    companion object: KLogging()

    @Test
    fun `path sink defaults do not append`() {
        val sink = Path.of("out.csv").asSink()

        log.debug { "sink=$sink" }
        sink.append.shouldBeFalse()
    }

    @Test
    fun `output stream sink defaults to caller-owned close`() {
        val sink = ByteArrayOutputStream().asSink()

        log.debug { "sink=$sink" }
        sink.closeOutput.shouldBeFalse()
    }
}

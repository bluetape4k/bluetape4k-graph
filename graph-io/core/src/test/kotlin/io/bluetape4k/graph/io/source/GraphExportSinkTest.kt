package io.bluetape4k.graph.io.source

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class GraphExportSinkTest {

    @Test
    fun `path sink defaults do not append`() {
        val sink = GraphExportSink.PathSink(Path.of("out.csv"))
        sink.append shouldBeEqualTo false
    }

    @Test
    fun `output stream sink defaults to caller-owned close`() {
        val sink = GraphExportSink.OutputStreamSink(ByteArrayOutputStream())
        sink.closeOutput shouldBeEqualTo false
    }
}

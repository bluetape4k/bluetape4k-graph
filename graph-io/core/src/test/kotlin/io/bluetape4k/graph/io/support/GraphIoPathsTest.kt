package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GraphIoPathsTest {

    @Test
    fun `openReader honors path source`(@TempDir dir: Path) {
        val file = dir.resolve("a.txt").also { Files.writeString(it, "x\ny") }
        val src = GraphImportSource.PathSource(file)
        GraphIoPaths.openReader(src).use { r -> r.readLines().size shouldBeEqualTo 2 }
    }

    @Test
    fun `openWriter creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("nested/a.txt")
        GraphIoPaths.openWriter(GraphExportSink.PathSink(nested)).use { it.write("hi") }
        Files.exists(nested) shouldBeEqualTo true
    }
}

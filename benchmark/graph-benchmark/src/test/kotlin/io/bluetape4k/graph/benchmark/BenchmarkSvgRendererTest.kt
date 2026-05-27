package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BenchmarkSvgRendererTest {

    @Test
    fun `renderer writes grouped svg from benchmark json`(@TempDir dir: Path) {
        val input = dir.resolve("sample-main.json").also {
            it.writeText(
                """
                [
                  {
                    "benchmark": "io.bluetape4k.graph.benchmark.SampleBenchmark.syncRead",
                    "primaryMetric": {"score": 1.2, "scoreError": 0.1, "scoreUnit": "ms/op"}
                  },
                  {
                    "benchmark": "io.bluetape4k.graph.benchmark.SampleBenchmark.virtualThreadRead",
                    "primaryMetric": {"score": 2.4, "scoreError": 0.2, "scoreUnit": "ms/op"}
                  }
                ]
                """.trimIndent(),
            )
        }

        val process = ProcessBuilder("python3", repoRoot().resolve("benchmark/scripts/render_benchmark_svg.py").toString(), input.toString())
            .directory(dir.toFile())
            .start()
        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()

        process.waitFor() shouldBeEqualTo 0
        stderr shouldBeEqualTo ""
        stdout shouldContain "docs/benchmark-results/SampleBenchmark.svg"

        val svg = dir.resolve("docs/benchmark-results/SampleBenchmark.svg")
        svg.exists().shouldBeTrue()
        val content = svg.readText()
        content shouldContain "SampleBenchmark"
        content shouldContain "syncRead"
        content shouldContain "virtualThreadRead"
        content shouldContain "ms/op"
    }

    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: error("Repository root not found")
        }
        return current
    }
}

package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class BenchmarkContainerLifecycleContractTest {

    @Test
    fun `benchmark startup paths do not enable container reuse implicitly`() {
        val benchmarkSources = listOf(
            "GraphDbComparisonBenchmark.kt",
            "GraphWriteIngestionBenchmark.kt",
        )

        benchmarkSources.forEach { sourceName ->
            benchmarkSource(sourceName).readText() shouldNotContain "reuse = true"
        }
    }

    @Test
    fun `container reuse is disabled by default`() {
        BenchmarkContainerReuse.isEnabled(environment = emptyMap()).shouldBeFalse()
    }

    @Test
    fun `developer can explicitly enable container reuse locally`() {
        BenchmarkContainerReuse.isEnabled(
            environment = mapOf(BenchmarkContainerReuse.ENV_NAME to "true"),
        ).shouldBeTrue()
    }

    @Test
    fun `CI cannot enable container reuse`() {
        BenchmarkContainerReuse.isEnabled(
            environment = mapOf(BenchmarkContainerReuse.ENV_NAME to "true", "CI" to "1"),
        ).shouldBeFalse()
        BenchmarkContainerReuse.isEnabled(
            environment = mapOf(BenchmarkContainerReuse.ENV_NAME to "true", "GITHUB_ACTIONS" to "true"),
        ).shouldBeFalse()
    }

    private fun benchmarkSource(name: String): Path =
        repoRoot()
            .resolve("benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark")
            .resolve(name)

    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: error("Repository root not found")
        }
        return current
    }
}

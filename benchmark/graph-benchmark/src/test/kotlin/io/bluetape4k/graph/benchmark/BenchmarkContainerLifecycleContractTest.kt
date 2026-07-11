package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
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

    @Test
    fun `empty CI marker still disables container reuse`() {
        BenchmarkContainerReuse.isEnabled(
            environment = mapOf(BenchmarkContainerReuse.ENV_NAME to "true", "CI" to ""),
        ).shouldBeFalse()
    }

    @Test
    fun `empty GitHub Actions marker still disables container reuse`() {
        BenchmarkContainerReuse.isEnabled(
            environment = mapOf(BenchmarkContainerReuse.ENV_NAME to "true", "GITHUB_ACTIONS" to ""),
        ).shouldBeFalse()
    }

    @Test
    fun `reusable FalkorDB servers survive trial teardown while clients close`() {
        var operationsCloseCount = 0
        var driverCloseCount = 0
        var serverCloseCount = 0

        BenchmarkFalkorLifecycle.close(
            reusableServer = true,
            closeOperations = {
                operationsCloseCount++
                error("operations close failure must not block remaining cleanup")
            },
            closeDriver = { driverCloseCount++ },
            closeServer = { serverCloseCount++ },
        )

        operationsCloseCount shouldBeEqualTo 1
        driverCloseCount shouldBeEqualTo 1
        serverCloseCount shouldBeEqualTo 0
    }

    @Test
    fun `non-reusable FalkorDB server closes after clients`() {
        val closeOrder = mutableListOf<String>()

        BenchmarkFalkorLifecycle.close(
            reusableServer = false,
            closeOperations = { closeOrder += "operations" },
            closeDriver = { closeOrder += "driver" },
            closeServer = { closeOrder += "server" },
        )

        closeOrder shouldBeEqualTo listOf("operations", "driver", "server")
    }

    @Test
    fun `benchmark states delegate FalkorDB teardown to lifecycle policy`() {
        val benchmarkSources = listOf(
            "GraphDbComparisonBenchmark.kt",
            "GraphWriteIngestionBenchmark.kt",
        )

        benchmarkSources.forEach { sourceName ->
            val source = benchmarkSource(sourceName).readText()
            source shouldContain "BenchmarkFalkorLifecycle.close("
            source shouldContain "closeOperations = { ops.close() }"
            source shouldContain "closeDriver = { falkorDriver?.close() }"
            source shouldContain "closeServer = { falkorServer?.close() }"
        }
    }

    @Test
    fun `PR CI detects graph benchmark changes and runs lifecycle tests`() {
        val workflow = repoRoot().resolve(".github/workflows/ci.yml").readText()

        workflow shouldContain "graph-benchmark:"
        workflow shouldContain "- 'benchmark/graph-benchmark/**'"
        workflow shouldContain "test-graph-benchmark:"
        workflow shouldContain ":graph-benchmark:test"
        workflow shouldContain "- test-graph-benchmark"
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

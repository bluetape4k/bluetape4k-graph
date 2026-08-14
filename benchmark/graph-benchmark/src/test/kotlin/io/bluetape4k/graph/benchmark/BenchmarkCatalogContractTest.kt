package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class BenchmarkCatalogContractTest {

    @Test
    fun `catalog matches every registered benchmark module`() {
        val root = repoRoot()
        val catalog = root.resolve("benchmark/benchmark-modules.json").readText()
        val catalogProjects = Regex("\\\"project\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .findAll(catalog)
            .map { it.groupValues[1] }
            .toList()
        val registeredProjects = Files.list(root.resolve("benchmark")).use { paths ->
            paths.filter { Files.isDirectory(it) && Files.exists(it.resolve("build.gradle.kts")) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        catalogProjects.sorted() shouldBeEqualTo registeredProjects
        catalogProjects.distinct().size shouldBeEqualTo catalogProjects.size
        catalogProjects.forEach { project ->
            root.resolve("benchmark/$project/build.gradle.kts").exists().shouldBeTrue()
        }
    }

    @Test
    fun `catalog drives CI and manual benchmark workflows`() {
        val root = repoRoot()
        val ci = root.resolve(".github/workflows/ci.yml").readText()
        val benchmark = root.resolve(".github/workflows/benchmark.yml").readText()

        ci shouldContain "graph-benchmarks:"
        ci shouldContain "benchmark-catalog:"
        ci shouldContain "matrix: \${{ fromJSON(needs.benchmark-catalog.outputs.matrix) }}"
        ci shouldContain ":\${{ matrix.project }}:test"
        ci shouldContain "name: test-results-\${{ matrix.project }}"
        benchmark shouldContain "benchmark/benchmark-modules.json"
        benchmark shouldContain "matrix: \${{ fromJSON(needs.benchmark-catalog.outputs.matrix) }}"
        benchmark shouldContain "run: ./gradlew \":\${{ matrix.project }}:benchmark\""
        benchmark shouldContain "name: \${{ matrix.id }}-results"
    }

    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: error("Repository root not found")
        }
        return current
    }
}

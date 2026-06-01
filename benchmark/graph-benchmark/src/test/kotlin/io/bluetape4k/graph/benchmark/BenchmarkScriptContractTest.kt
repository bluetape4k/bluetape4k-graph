package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class BenchmarkScriptContractTest {

    @Test
    fun `age benchmark wrapper emits one valid json summary line`(@TempDir dir: Path) {
        val reportRoot = jmhReportRoot(dir, "age")
        writeJmhReport(
            reportRoot.resolve("main.json"),
            """
            [
              {"benchmark": "pkg.CreateVertexBenchmark.createVertex", "primaryMetric": {"score": 1.5, "scoreUnit": "ms/op"}},
              {"benchmark": "pkg.FindVerticesBenchmark.findVertices", "primaryMetric": {"score": 250.0, "scoreUnit": "us/op"}}
            ]
            """.trimIndent(),
        )

        val result = runScript(
            "benchmark-age.sh",
            "BENCHMARK_SKIP_RUN" to "true",
            "BENCHMARK_AGE_REPORT_ROOT" to reportRoot.parent.toString(),
        )

        result.exitCode shouldBeEqualTo 0
        result.stdoutLines shouldHaveSingleLineContaining "\"primary\": 875"
        result.stdoutLines.single() shouldContain "\"createVertex\": 1500"
        result.stdoutLines.single() shouldContain "\"findVertices\": 250"
        assertJson(result.stdoutLines.single())
    }

    @Test
    fun `neo4j age benchmark wrapper emits combined age and neo4j keys`(@TempDir dir: Path) {
        val ageRoot = jmhReportRoot(dir, "age")
        val neo4jRoot = jmhReportRoot(dir, "neo4j")
        writeJmhReport(
            ageRoot.resolve("main.json"),
            """
            [
              {"benchmark": "pkg.CreateVertexBenchmark.createVertex", "primaryMetric": {"score": 1.0, "scoreUnit": "ms/op"}}
            ]
            """.trimIndent(),
        )
        writeJmhReport(
            neo4jRoot.resolve("main.json"),
            """
            [
              {"benchmark": "pkg.Neo4jCreateVertexBenchmark.createVertex", "primaryMetric": {"score": 2.0, "scoreUnit": "ms/op"}}
            ]
            """.trimIndent(),
        )

        val result = runScript(
            "benchmark-neo4j-age.sh",
            "BENCHMARK_SKIP_RUN" to "true",
            "BENCHMARK_AGE_REPORT_ROOT" to ageRoot.parent.toString(),
            "BENCHMARK_NEO4J_REPORT_ROOT" to neo4jRoot.parent.toString(),
        )

        result.exitCode shouldBeEqualTo 0
        result.stdoutLines shouldHaveSingleLineContaining "\"primary\": 1500"
        result.stdoutLines.single() shouldContain "\"schema\": \"bluetape4k.graph.backend-benchmark-summary.v1\""
        result.stdoutLines.single() shouldContain "\"unit\": \"us/op\""
        result.stdoutLines.single() shouldContain "\"direction\": \"lower_is_better\""
        result.stdoutLines.single() shouldContain "\"benchmarks\""
        result.stdoutLines.single() shouldContain "\"age_createVertex\": 1000"
        result.stdoutLines.single() shouldContain "\"neo4j_createVertex\": 2000"
        assertJson(result.stdoutLines.single())
    }

    @Test
    fun `age benchmark wrapper fails when report is missing`(@TempDir dir: Path) {
        val reportRoot = dir.resolve("missing")

        val result = runScript(
            "benchmark-age.sh",
            "BENCHMARK_SKIP_RUN" to "true",
            "BENCHMARK_AGE_REPORT_ROOT" to reportRoot.toString(),
        )

        (result.exitCode != 0).shouldBeTrue()
        result.stderr shouldContain "ERROR: benchmark JSON report not found"
        result.stdoutLines shouldBeEqualTo emptyList()
    }

    @Test
    fun `neo4j age benchmark wrapper explains malformed reports`(@TempDir dir: Path) {
        val ageRoot = jmhReportRoot(dir, "age")
        val neo4jRoot = jmhReportRoot(dir, "neo4j")
        writeJmhReport(
            ageRoot.resolve("main.json"),
            """
            [
              {"benchmark": "pkg.CreateVertexBenchmark.createVertex", "primaryMetric": {"score": 1.0, "scoreUnit": "ms/op"}}
            ]
            """.trimIndent(),
        )
        writeJmhReport(neo4jRoot.resolve("main.json"), """{"not": "a JMH list"}""")

        val result = runScript(
            "benchmark-neo4j-age.sh",
            "BENCHMARK_SKIP_RUN" to "true",
            "BENCHMARK_AGE_REPORT_ROOT" to ageRoot.parent.toString(),
            "BENCHMARK_NEO4J_REPORT_ROOT" to neo4jRoot.parent.toString(),
        )

        (result.exitCode != 0).shouldBeTrue()
        result.stderr shouldContain "ERROR: Expected JMH result list for neo4j report"
        result.stdoutLines shouldBeEqualTo emptyList()
    }

    private fun jmhReportRoot(dir: Path, name: String): Path =
        dir.resolve(name).resolve("main").also { it.createDirectories() }

    private fun writeJmhReport(path: Path, content: String) {
        path.parent.createDirectories()
        path.writeText(content)
    }

    private fun runScript(scriptName: String, vararg env: Pair<String, String>): ProcessResult {
        val process = ProcessBuilder(repoRoot().resolve("scripts").resolve(scriptName).toString())
            .directory(repoRoot().toFile())
            .redirectErrorStream(false)
            .apply {
                environment().putAll(env.toMap())
            }
            .start()
        return ProcessResult(
            exitCode = process.waitFor(),
            stdout = process.inputStream.bufferedReader().readText().trim(),
            stderr = process.errorStream.bufferedReader().readText().trim(),
        )
    }

    private fun assertJson(json: String) {
        val process = ProcessBuilder("python3", "-c", "import json,sys; json.loads(sys.argv[1])", json)
            .start()
        process.waitFor() shouldBeEqualTo 0
    }

    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: error("Repository root not found")
        }
        return current
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val stdoutLines: List<String> = stdout.lines().filter { it.isNotBlank() }
    }
}

private infix fun List<String>.shouldHaveSingleLineContaining(expected: String) {
    this.size shouldBeEqualTo 1
    this.single() shouldContain expected
}

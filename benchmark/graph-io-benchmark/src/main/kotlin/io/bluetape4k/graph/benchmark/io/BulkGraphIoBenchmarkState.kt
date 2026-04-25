package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

@State(Scope.Benchmark)
open class BulkGraphIoBenchmarkState {

    @Param("small", "medium")
    var sizeName: String = "small"

    lateinit var ops: GraphOperations
    lateinit var tempDir: Path

    @Setup(Level.Trial)
    fun setup() {
        tempDir = Files.createTempDirectory("graph-io-bench")
        ops = TinkerGraphOperations()

        val (vCount, eCount) = when (sizeName) {
            "small" -> 1_000 to 2_000
            "medium" -> 10_000 to 20_000
            else -> 100_000 to 200_000
        }

        val vertexIds = ArrayList<GraphElementId>(vCount)
        for (i in 0 until vCount) {
            vertexIds += ops.createVertex("Person", mapOf("i" to i, "name" to "n$i")).id
        }

        val rng = Random(42)
        for (i in 0 until eCount) {
            val a = vertexIds[rng.nextInt(vCount)]
            val b = vertexIds[rng.nextInt(vCount)]
            ops.createEdge(a, b, "KNOWS", mapOf("since" to (2000 + rng.nextInt(25))))
        }
    }

    @TearDown(Level.Trial)
    fun teardown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}

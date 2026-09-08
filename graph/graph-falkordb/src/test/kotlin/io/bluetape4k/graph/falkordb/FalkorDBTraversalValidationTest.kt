package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class FalkorDBTraversalValidationTest {

    private val driver = mockk<com.falkordb.Driver>()
    private val graphName = "test"
    private val unsafeLabel = "KNOWS) MATCH (other)"
    private val startId = GraphElementId.of("0")
    private val endId = GraphElementId.of("1")

    init {
        every { driver.graph(graphName) } throws AssertionError("driver call must not be reached")
    }

    @Test
    fun `sync neighbors는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.neighbors(startId, NeighborOptions(edgeLabel = unsafeLabel))
        }

        ex.message.shouldContain("valid identifier")
    }

    @Test
    fun `sync shortestPath는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.shortestPath(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        ex.message.shouldContain("valid identifier")
    }

    @Test
    fun `sync allPaths는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.allPaths(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        ex.message.shouldContain("valid identifier")
    }

    @Test
    fun `suspend neighbors는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.neighbors(startId, NeighborOptions(edgeLabel = unsafeLabel)).toList()
        }

        ex.message.shouldContain("valid identifier")
    }

    @Test
    fun `suspend shortestPath는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.shortestPath(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        ex.message.shouldContain("valid identifier")
    }

    @Test
    fun `suspend allPaths는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.allPaths(startId, endId, PathOptions(edgeLabel = unsafeLabel)).toList()
        }

        ex.message.shouldContain("valid identifier")
    }
}

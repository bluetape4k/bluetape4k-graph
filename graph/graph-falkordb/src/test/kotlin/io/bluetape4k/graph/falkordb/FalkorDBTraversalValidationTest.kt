package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FalkorDBTraversalValidationTest {

    companion object: KLogging()

    private val driver = mockk<com.falkordb.Driver>(relaxed = true)
    private val graphName = "test"
    private val unsafeLabel = "KNOWS) MATCH (other)"
    private val startId = 0.toGraphElementId()
    private val endId = 1.toGraphElementId()

    @BeforeEach
    fun beforeEach() {
        clearMocks(driver)
        every { driver.graph(graphName) } throws AssertionError("driver call must not be reached")
    }

    @Test
    fun `sync neighbors는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.neighbors(startId, NeighborOptions(edgeLabel = unsafeLabel))
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `sync shortestPath는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.shortestPath(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `sync allPaths는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() {
        val ops = FalkorDBGraphOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.allPaths(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `suspend neighbors는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.neighbors(startId, NeighborOptions(edgeLabel = unsafeLabel)).toList()
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `suspend shortestPath는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.shortestPath(startId, endId, PathOptions(edgeLabel = unsafeLabel))
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `suspend allPaths는 안전하지 않은 edgeLabel을 driver 호출 전에 거부한다`() = runSuspendIO {
        val ops = FalkorDBGraphSuspendOperations(driver, graphName)

        val ex = assertFailsWith<IllegalArgumentException> {
            ops.allPaths(startId, endId, PathOptions(edgeLabel = unsafeLabel)).toList()
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "valid identifier"
    }
}

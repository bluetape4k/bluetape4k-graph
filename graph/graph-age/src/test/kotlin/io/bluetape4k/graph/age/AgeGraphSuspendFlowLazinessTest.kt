package io.bluetape4k.graph.age

import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test

class AgeGraphSuspendFlowLazinessTest {

    @Test
    fun `bfs Flow는 수집할 때까지 동기 delegate를 호출하지 않는다`() = runSuspendIO {
        val startId = 1L.toGraphElementId()
        val options = TreeTraversalOptions()

        val operations = AgeGraphSuspendOperations(mockk<Database>(relaxed = true), "test")

        operations.bfs(startId, options) shouldBeInstanceOf Flow::class
    }
}

package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions
import org.junit.jupiter.api.Test

class TinkerGraphTraversalFailuresTest {

    @Test
    fun `unexpected traversal failure is wrapped with backend context`() {
        val cause = IllegalStateException("traversal engine failed")

        val ex = tinkerGraphTraversalFailure(
            operation = "shortestPath",
            fromId = GraphElementId.of("1"),
            toId = GraphElementId.of("2"),
            options = PathOptions(edgeLabel = "KNOWS"),
            cause = cause,
        )

        ex.message shouldContain "TinkerGraph shortestPath traversal failed"
        ex.message shouldContain "from=1"
        ex.message shouldContain "to=2"
        ex.cause shouldBeInstanceOf IllegalStateException::class
    }

    @Test
    fun `existing GraphQueryException is preserved`() {
        val original = GraphQueryException("already contextual")

        val ex = tinkerGraphTraversalFailure(
            operation = "allPaths",
            fromId = GraphElementId.of("1"),
            toId = GraphElementId.of("2"),
            options = PathOptions(),
            cause = original,
        )

        ex.message shouldContain "already contextual"
    }
}

package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphIoCompositeProgressListenerTest {

    private val event = GraphIoProgressEvent(
        runId = 1L,
        type = GraphIoProgressEventType.PROGRESS,
        operation = GraphIoOperation.IMPORT,
        format = GraphIoFormat.CSV,
    )

    @Test
    fun `delegates are called in order and exception does not stop later delegates`() {
        val calls = mutableListOf<String>()
        val composite = GraphIoCompositeProgressListener.of(
            GraphIoProgressListener { calls += "first" },
            GraphIoProgressListener {
                calls += "second"
                throw IllegalStateException("secret-message")
            },
            GraphIoProgressListener { calls += "third" },
        )

        assertFailsWith<IllegalStateException> { composite.onEvent(event) }
        calls shouldBeEqualTo listOf("first", "second", "third")
    }

    @Test
    fun `error is rethrown after all delegates and preserves identity`() {
        val calls = mutableListOf<String>()
        val error = AssertionError("listener-error")
        val composite = GraphIoCompositeProgressListener.of(
            GraphIoProgressListener {
                calls += "first"
                throw error
            },
            GraphIoProgressListener { calls += "second" },
        )

        val thrown = assertFailsWith<AssertionError> { composite.onEvent(event) }
        (thrown === error) shouldBeEqualTo true
        calls shouldBeEqualTo listOf("first", "second")
    }

    @Test
    fun `empty composite is a no-op`() {
        GraphIoCompositeProgressListener.of().onEvent(event)
    }

    @Test
    fun `reporter dispatch hook warns once per exception and continues`() {
        val calls = mutableListOf<String>()
        var warnings = 0
        val composite = GraphIoCompositeProgressListener.of(
            GraphIoProgressListener { calls += "first" },
            GraphIoProgressListener {
                calls += "second"
                throw IllegalArgumentException("redacted")
            },
            GraphIoProgressListener { calls += "third" },
        )

        composite.dispatch(
            event = event,
            onException = { warnings++ },
            rethrowExceptions = false,
        )

        calls shouldBeEqualTo listOf("first", "second", "third")
        warnings shouldBeEqualTo 1
    }
}

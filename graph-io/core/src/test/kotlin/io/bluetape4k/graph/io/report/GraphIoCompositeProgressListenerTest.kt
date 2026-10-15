package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBe
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class GraphIoCompositeProgressListenerTest {

    companion object: KLogging()

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
            { calls += "first" },
            {
                calls += "second"
                error("secret-message")
            },
            { calls += "third" },
        )

        assertFailsWith<IllegalStateException> {
            composite.onEvent(event)
        }
        calls shouldBeEqualTo listOf("first", "second", "third")
    }

    @Test
    fun `error is rethrown after all delegates and preserves identity`() {
        val calls = mutableListOf<String>()
        val error = AssertionError("listener-error")
        val composite = GraphIoCompositeProgressListener.of(
            {
                calls += "first"
                throw error
            },
            { calls += "second" },
        )

        val thrown = assertFailsWith<AssertionError> {
            composite.onEvent(event)
        }
        thrown shouldBe error
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
            { calls += "first" },
            {
                calls += "second"
                throw IllegalArgumentException("redacted")
            },
            { calls += "third" },
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

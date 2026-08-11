package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import org.junit.jupiter.api.Test

class GraphIoReadExceptionTest {

    @Test
    fun `read exception keeps safe failure location without exposing raw payload or cause`() {
        val failure = GraphIoFailure(
            phase = GraphIoPhase.READ_VERTEX,
            location = "line:7",
            recordId = "v-7",
            message = "secret payload",
        )

        val error = GraphIoReadException(failure)

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
        error.failure.location shouldBeEqualTo "line:7"
        error.failure.recordId.shouldBeNull()
        error.failure.sourceName.shouldBeNull()
        error.failure.columnName.shouldBeNull()
        error.failure.elementName.shouldBeNull()
        error.failure.message shouldBeEqualTo "Graph IO read failed"
        error.failure.toString() shouldNotContain "secret payload"
        error.failure.toString() shouldNotContain "v-7"
        error.message.orEmpty() shouldContain "line:7"
        error.message.orEmpty() shouldNotContain "secret payload"
        error.message.orEmpty() shouldNotContain "v-7"
        error.toString() shouldNotContain "secret payload"
        error.toString() shouldNotContain "v-7"
        error.cause.shouldBeNull()
    }
}

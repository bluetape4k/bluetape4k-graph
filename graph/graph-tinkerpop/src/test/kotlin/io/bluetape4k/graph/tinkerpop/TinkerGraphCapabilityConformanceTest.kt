package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.model.elementIdOf
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

/** TinkerGraph를 빠른 in-memory reference lane으로 검증한다. */
class TinkerGraphCapabilityConformanceTest: AbstractGraphCapabilityConformanceTest() {

    private val delegate = TinkerGraphOperations()

    override val operations: GraphOperations = delegate

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> =
        backendCapabilities(transactional = true, boundedChunked = true)

    @Test
    fun `malformed and valid-missing IDs remain distinct in the conformance lane`() {
        val malformedId = elementIdOf("not-a-number")
        val missingId = elementIdOf("99999999")

        val failure = assertFailsWith<GraphQueryException> {
            operations.findVertexById(malformedId)
        }

        log.debug { "failure=$failure" }
        failure.message shouldContain "numeric ID"
        operations.findVertexById(missingId).shouldBeNull()
    }
}

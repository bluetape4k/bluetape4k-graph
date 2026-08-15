package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations

/** TinkerGraph를 빠른 in-memory reference lane으로 검증한다. */
class TinkerGraphCapabilityConformanceTest : AbstractGraphCapabilityConformanceTest() {

    private val delegate = TinkerGraphOperations()

    override val operations: GraphOperations
        get() = delegate

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)
}

package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

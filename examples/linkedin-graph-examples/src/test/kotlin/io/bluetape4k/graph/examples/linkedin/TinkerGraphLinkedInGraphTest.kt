package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphLinkedInGraphTest : AbstractLinkedInGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

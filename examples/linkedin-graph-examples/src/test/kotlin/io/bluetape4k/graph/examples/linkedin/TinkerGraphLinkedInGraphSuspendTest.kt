package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphLinkedInGraphSuspendTest: AbstractLinkedInGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

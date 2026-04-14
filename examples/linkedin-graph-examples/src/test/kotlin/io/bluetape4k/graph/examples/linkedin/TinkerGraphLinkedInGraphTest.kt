package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations

class TinkerGraphLinkedInGraphTest: AbstractLinkedInGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

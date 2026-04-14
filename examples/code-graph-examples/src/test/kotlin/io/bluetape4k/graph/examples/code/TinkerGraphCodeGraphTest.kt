package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations

class TinkerGraphCodeGraphTest: AbstractCodeGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

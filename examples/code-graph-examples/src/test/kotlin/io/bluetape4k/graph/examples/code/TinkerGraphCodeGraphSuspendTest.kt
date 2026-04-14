package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

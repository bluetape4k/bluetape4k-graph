package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.logging.KLogging

class TinkerGraphCodeGraphSuspendTest: AbstractCodeGraphSuspendTest() {

    companion object: KLogging()

    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

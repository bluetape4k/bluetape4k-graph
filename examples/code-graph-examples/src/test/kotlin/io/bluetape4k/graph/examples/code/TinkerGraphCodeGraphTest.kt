package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging

class TinkerGraphCodeGraphTest: AbstractCodeGraphTest() {

    companion object: KLogging()
    
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

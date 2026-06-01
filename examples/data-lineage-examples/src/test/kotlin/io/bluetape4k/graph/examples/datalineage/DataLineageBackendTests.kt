package io.bluetape4k.graph.examples.datalineage

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphDataLineageImpactTest: AbstractDataLineageImpactTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphDataLineageImpactSuspendTest: AbstractDataLineageImpactSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

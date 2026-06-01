package io.bluetape4k.graph.examples.networktopology

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphNetworkTopologyImpactTest: AbstractNetworkTopologyImpactTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphNetworkTopologyImpactSuspendTest: AbstractNetworkTopologyImpactSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

package io.bluetape4k.graph.examples.supplychain

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphSupplyChainImpactTest: AbstractSupplyChainImpactTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphSupplyChainImpactSuspendTest: AbstractSupplyChainImpactSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

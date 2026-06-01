package io.bluetape4k.graph.examples.securityattack

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

class TinkerGraphSecurityAttackPathTest: AbstractSecurityAttackPathTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphSecurityAttackPathSuspendTest: AbstractSecurityAttackPathSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

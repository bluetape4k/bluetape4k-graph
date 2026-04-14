package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer

class MemgraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}

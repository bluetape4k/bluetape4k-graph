package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.servers.MemgraphServer

class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}

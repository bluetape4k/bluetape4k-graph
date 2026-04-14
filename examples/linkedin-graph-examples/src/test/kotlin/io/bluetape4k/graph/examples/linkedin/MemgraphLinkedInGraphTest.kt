package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.servers.MemgraphServer

class MemgraphLinkedInGraphTest: AbstractLinkedInGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}

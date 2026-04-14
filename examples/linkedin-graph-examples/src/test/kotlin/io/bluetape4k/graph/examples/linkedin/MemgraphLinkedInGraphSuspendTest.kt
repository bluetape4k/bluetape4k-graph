package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer

class MemgraphLinkedInGraphSuspendTest: AbstractLinkedInGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}

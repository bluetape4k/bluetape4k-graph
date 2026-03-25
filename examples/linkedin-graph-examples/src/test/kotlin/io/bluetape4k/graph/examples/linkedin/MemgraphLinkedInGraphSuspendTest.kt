package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}

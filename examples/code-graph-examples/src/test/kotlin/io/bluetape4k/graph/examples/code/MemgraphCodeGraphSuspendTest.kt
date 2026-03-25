package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}

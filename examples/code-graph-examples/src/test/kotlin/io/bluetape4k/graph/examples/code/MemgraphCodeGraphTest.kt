package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}

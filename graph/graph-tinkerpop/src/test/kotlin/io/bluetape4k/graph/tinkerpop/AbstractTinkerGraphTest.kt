package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.algo.asVirtualThread
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractTinkerGraphTest {

    companion object: KLogging()

    protected val ops = TinkerGraphOperations()
    protected val suspendOps = TinkerGraphSuspendOperations(ops)
    protected val vtOps by lazy { ops.asVirtualThread() }

    @BeforeEach
    open fun beforeEach() {
        ops.dropGraph("default")
    }

    @AfterAll
    open fun afterAll() {
        suspendOps.close()
    }
}

package io.bluetape4k.graph.vt

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualThreadOperationsAdapterTest {

    companion object: KLogging()

    private lateinit var ops: TinkerGraphOperations
    private lateinit var vtOps: VirtualThreadOperationsAdapter

    @BeforeEach
    fun setUp() {
        ops = TinkerGraphOperations()
        vtOps = VirtualThreadOperationsAdapter(ops)
    }

    @AfterEach
    fun tearDown() {
        ops.close()
    }

    @Test
    fun `createVertexAsync works through facade`() {
        val v = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
        v.shouldNotBeNull()
        v.label shouldBeEqualTo "Person"
    }

    @Test
    fun `createEdgeAsync works through facade`() {
        val from = ops.createVertex("Person")
        val to = ops.createVertex("Person")
        val edge = vtOps.createEdgeAsync(from.id, to.id, "KNOWS").join()
        edge.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
    }

    @Test
    fun `close does not affect delegate`() {
        vtOps.close()
        // delegate 는 여전히 사용 가능
        val v = ops.createVertex("Person")
        v.shouldNotBeNull()
    }

    @Test
    fun `asVirtualThread returns GraphVirtualThreadOperations`() {
        val result: io.bluetape4k.graph.repository.GraphVirtualThreadOperations = ops.asVirtualThread()
        result.shouldNotBeNull()
    }

    @Test
    fun `facade operations are thread-safe under concurrent load`() {
        val v = ops.createVertex("Person", mapOf("name" to "Alice"))
        StructuredTaskScopeTester()
            .rounds(50)
            .add { vtOps.findVertexByIdAsync("Person", v.id).join().shouldNotBeNull() }
            .run()
    }
}

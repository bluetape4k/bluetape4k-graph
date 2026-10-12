package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.model.batchEdgeOf
import io.bluetape4k.graph.repository.GraphVirtualThreadCapabilitiesOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Runtimex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualThreadOperationsAdapterTest: AbstractGraphTest() {

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
        val v = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).get()

        log.debug { "v=$v" }
        v.shouldNotBeNull()
        v.label shouldBeEqualTo "Person"
    }

    @Test
    fun `createEdgeAsync works through facade`() {
        val from = ops.createVertex("Person")
        val to = ops.createVertex("Person")
        val edge = vtOps.createEdgeAsync(from.id, to.id, "KNOWS").get()

        log.debug { "edge=$edge" }
        edge.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
    }

    @Test
    fun `batch create async works through facade`() {
        val vertices = vtOps
            .createVerticesAsync(
                "Person",
                listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob"))
            )
            .join()
        log.debug { "vertices=$vertices" }

        val edge = vtOps
            .createEdgesAsync(
                "KNOWS",
                listOf(batchEdgeOf(vertices[0].id, vertices[1].id))
            )
            .get()
            .single()
        log.debug { "edge=$edge" }
        
        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")
        edge.label shouldBeEqualTo "KNOWS"
    }

    @Test
    fun `close does not affect delegate`() {
        val virtualThread: GraphVirtualThreadOperations = vtOps
        virtualThread.close()

        // delegate 는 여전히 사용 가능
        val v = ops.createVertex("Person")
        v.shouldNotBeNull()
    }

    @Test
    fun `asVirtualThread returns GraphVirtualThreadOperations`() {
        val result: GraphVirtualThreadOperations = ops.asVirtualThread()
        result.shouldNotBeNull()
    }

    @Test
    fun `facade type preserves capability contract`() {
        val virtualThread: GraphVirtualThreadOperations = vtOps
        val capabilities: GraphVirtualThreadCapabilitiesOperations = virtualThread

        capabilities.capabilities() shouldBeEqualTo ops.capabilities()
    }

    @Test
    fun `facade operations are thread-safe under concurrent load`() {
        val v = ops.createVertex("Person", mapOf("name" to "Alice"))

        StructuredTaskScopeTester()
            .rounds(Runtimex.availableProcessors * 4)
            .add {
                val vertex = vtOps.findVertexByIdAsync("Person", v.id).get()
                vertex.shouldNotBeNull()
            }
            .run()
    }
}

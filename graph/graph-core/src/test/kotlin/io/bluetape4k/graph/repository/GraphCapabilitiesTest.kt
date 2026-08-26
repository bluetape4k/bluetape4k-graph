package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.junit.jupiter.api.Test

class GraphCapabilitiesTest {

    @Test
    fun `capability names and existing ordinals remain serialization compatible`() {
        GraphCapability.MERGE.ordinal shouldBeEqualTo 0
        GraphCapability.SCHEMA.ordinal shouldBeEqualTo 1
        GraphCapability.TRANSACTION.ordinal shouldBeEqualTo 2
        GraphCapability.BATCH_INSERT.ordinal shouldBeEqualTo 3
        GraphCapability.CHUNKED_READ.ordinal shouldBeEqualTo 4
        GraphCapability.CHUNKED_EXPORT.ordinal shouldBeEqualTo 5
        GraphCapability.WEIGHTED_PATH.ordinal shouldBeEqualTo 6
        GraphCapability.GRAPH_ALGORITHM.ordinal shouldBeEqualTo 7
        GraphCapability.NATIVE_ALGORITHM.ordinal shouldBeEqualTo 8
        GraphCapability.BOUNDED_CHUNKED_READ.ordinal shouldBeEqualTo 9
        GraphCapability.BOUNDED_CHUNKED_EXPORT.ordinal shouldBeEqualTo 10

        GraphCapability.fromSerializedNameOrNull("BOUNDED_CHUNKED_READ") shouldBeEqualTo
            GraphCapability.BOUNDED_CHUNKED_READ
        GraphCapability.fromSerializedNameOrNull("CAPABILITY_ADDED_LATER").shouldBeNull()
        GraphCapability.fromSerializedNameOrNull("bounded_chunked_read").shouldBeNull()
    }

    @Test
    fun `blocking capability lookup reflects declared backend contracts`() {
        val capabilities = TinkerGraphOperations().capabilities()

        capabilities.supports(GraphCapability.MERGE).shouldBeTrue()
        capabilities.supports(GraphCapability.SCHEMA).shouldBeTrue()
        capabilities.supports(GraphCapability.TRANSACTION).shouldBeTrue()
        capabilities.supports(GraphCapability.BATCH_INSERT).shouldBeTrue()
        capabilities.supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
        capabilities.supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
        capabilities.supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeTrue()
        capabilities.supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeTrue()
        capabilities.constraints(GraphCapability.CHUNKED_READ).contains("api-chunking-only").shouldBeTrue()
        capabilities.constraints(GraphCapability.CHUNKED_EXPORT).contains("api-chunking-only").shouldBeTrue()
        capabilities.constraints(GraphCapability.BOUNDED_CHUNKED_READ)
            .contains("native-traversal-bounded").shouldBeTrue()
        capabilities.constraints(GraphCapability.BOUNDED_CHUNKED_EXPORT)
            .contains("native-traversal-bounded").shouldBeTrue()
        capabilities.supports(GraphCapability.WEIGHTED_PATH).shouldBeTrue()
        capabilities.supports(GraphCapability.GRAPH_ALGORITHM).shouldBeTrue()
        capabilities.supports(GraphCapability.NATIVE_ALGORITHM).shouldBeFalse()
        capabilities.version(GraphCapability.MERGE) shouldBeEqualTo "core-0.7"
    }

    @Test
    fun `suspend capability lookup reflects declared backend contracts`() = runSuspendIO {
        val operations = TinkerGraphSuspendOperations()

        operations.capabilities().supports(GraphCapability.MERGE).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.SCHEMA).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.TRANSACTION).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.BATCH_INSERT).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.WEIGHTED_PATH).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.GRAPH_ALGORITHM).shouldBeTrue()
        operations.capabilities().supports(GraphCapability.NATIVE_ALGORITHM).shouldBeFalse()
        operations.close()
    }

    @Test
    fun `virtual thread adapter preserves delegate capability mapping`() {
        val operations = TinkerGraphOperations()
        val virtualThread = VirtualThreadOperationsAdapter(operations)

        virtualThread.capabilities() shouldBeEqualTo operations.capabilities()
        virtualThread.close()
        operations.close()
    }

    @Test
    fun `decorator must explicitly preserve capability mapping`() {
        val delegate = TinkerGraphOperations()
        val decorator: GraphOperations = GraphOperationsDecorator(delegate)

        decorator.capabilities().supports(GraphCapability.MERGE).shouldBeFalse()
        decorator.capabilities().supports(GraphCapability.SCHEMA).shouldBeFalse()
        decorator.capabilities().supports(GraphCapability.TRANSACTION).shouldBeFalse()
        decorator.capabilities().supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
        decorator.capabilities().supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
        decorator.capabilities().supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeFalse()
        decorator.capabilities().supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeFalse()
        decorator.close()
    }

    @Test
    fun `bounded capability requires its API chunk capability`() {
        assertFailsWith<IllegalArgumentException> {
            GraphCapabilities(supported = setOf(GraphCapability.BOUNDED_CHUNKED_READ))
        }
        assertFailsWith<IllegalArgumentException> {
            GraphCapabilities(supported = setOf(GraphCapability.BOUNDED_CHUNKED_EXPORT))
        }
    }

    private class GraphOperationsDecorator(
        delegate: GraphOperations,
    ): GraphOperations by delegate
}

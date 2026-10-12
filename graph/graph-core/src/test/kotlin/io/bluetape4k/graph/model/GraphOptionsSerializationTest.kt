package io.bluetape4k.graph.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.io.lookup
import io.bluetape4k.io.serializer.BinarySerializationException
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.lang.reflect.Modifier

class GraphOptionsSerializationTest: AbstractGraphTest() {

    private companion object: KLogging() {
        val unsafe: Unsafe by lazy {
            Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        }

        private fun objectWithField(value: Any, fieldName: String, replacement: Any?): Any {
            val clone = unsafe.allocateInstance(value.javaClass)
            generateSequence(value.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .filterNot { Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    field.isAccessible = true
                    field.set(clone, field.get(value))
                }

            value.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.set(clone, replacement)
            return clone
        }
    }

    @Nested
    inner class JavaSerialization {

        @Test
        fun `traversal option들은 모든 public property를 Java serialization round-trip에서 보존한다`() {
            val options = listOf(
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 0),
                PathOptions(
                    edgeLabel = "ROAD",
                    maxDepth = 2,
                    weightProperty = "cost",
                    missingWeightPolicy = MissingWeightPolicy.UseDefault(1.5),
                    direction = Direction.INCOMING,
                    maxVisited = 200,
                ),
                BfsDfsOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING, maxDepth = 0, maxVertices = 25),
                CycleOptions(vertexLabel = "Person", edgeLabel = "KNOWS", maxDepth = 3, maxCycles = 7),
            )

            options.forEach { option ->
                deserialize<GraphTraversalOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        @Test
        fun `algorithm option들은 모든 public property를 Java serialization round-trip에서 보존한다`() {
            val options = listOf<GraphAlgorithmOptions>(
                PageRankOptions(
                    vertexLabel = "Person",
                    edgeLabel = "KNOWS",
                    iterations = 30,
                    dampingFactor = 0.9,
                    tolerance = 1e-6,
                    topK = 10,
                ),
                DegreeOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING),
                ComponentOptions(vertexLabel = "Person", edgeLabel = "KNOWS", weakly = false, minSize = 2),
            )

            options.forEach { option ->
                deserialize<GraphAlgorithmOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        @Test
        fun `모든 serializable option의 serialVersionUID는 1L로 고정된다`() {
            val options = listOf(
                NeighborOptions(),
                PathOptions(),
                BfsDfsOptions(),
                CycleOptions(),
                PageRankOptions(),
                DegreeOptions(),
                ComponentOptions(),
                MissingWeightPolicy.Fail,
                MissingWeightPolicy.Skip,
                MissingWeightPolicy.UseDefault(1.0),
            )

            options.forEach { option ->
                option::class.lookup().serialVersionUID shouldBeEqualTo 1L
            }
        }

        @Test
        fun `traversal option의 invalid serialized payload는 invariant 메시지와 함께 거부된다`() {
            val invalidPayloads = listOf(
                serializedWithField(NeighborOptions(), "maxDepth", -1) to listOf("maxDepth", "-1"),
                serializedWithField(PathOptions(), "maxDepth", -1) to listOf("maxDepth", "-1"),
                serializedWithField(PathOptions(), "maxVisited", 0) to listOf("maxVisited", "0"),
                serializedWithField(BfsDfsOptions(), "maxDepth", -1) to listOf("maxDepth", "-1"),
                serializedWithField(BfsDfsOptions(), "maxVertices", 0) to listOf("maxVertices", "0"),
                serializedWithField(CycleOptions(), "maxDepth", 0) to listOf("maxDepth", "0"),
                serializedWithField(CycleOptions(), "maxDepth", -1) to listOf("maxDepth", "-1"),
                serializedWithField(CycleOptions(), "maxCycles", 0) to listOf("maxCycles", "0"),
            )

            invalidPayloads.forEach { (payload, expectedMessageParts) ->
                val ex = assertFailsWith<BinarySerializationException> {
                    deserialize<Any>(payload)
                }
                val cause = ex.cause.shouldNotBeNull()
                expectedMessageParts.forEach { cause.message shouldContain it }
            }
        }

        @Test
        fun `algorithm option의 invalid serialized payload는 invariant 메시지와 함께 거부된다`() {
            val invalidPayloads = listOf(
                serializedWithField(PageRankOptions(), "iterations", 0) to listOf("iterations", "0"),
                serializedWithField(PageRankOptions(), "topK", 0) to listOf("topK", "0"),
                serializedWithField(PageRankOptions(), "dampingFactor", Double.NaN) to listOf("dampingFactor", "NaN"),
                serializedWithField(PageRankOptions(), "dampingFactor", Double.POSITIVE_INFINITY) to
                        listOf("dampingFactor", "Infinity"),
                serializedWithField(PageRankOptions(), "tolerance", 0.0) to listOf("tolerance", "0.0"),
                serializedWithField(PageRankOptions(), "tolerance", Double.POSITIVE_INFINITY) to
                        listOf("tolerance", "Infinity"),
                serializedWithField(ComponentOptions(), "minSize", 0) to listOf("minSize", "0"),
            )

            invalidPayloads.forEach { (payload, expectedMessageParts) ->
                val ex = assertFailsWith<BinarySerializationException> {
                    deserialize<Any>(payload)
                }
                log.debug { "ex.cause=${ex.cause}" }
                val cause = ex.cause.shouldNotBeNull()
                expectedMessageParts.forEach { cause.message shouldContain it }
            }
        }

        @Test
        fun `nested missing weight policy의 invalid serialized payload도 거부된다`() {
            val invalidPolicy = objectWithField(MissingWeightPolicy.UseDefault(1.0), "value", 0.0)
            val invalidPath = serialize(
                objectWithField(
                    PathOptions(weightProperty = "cost"),
                    "missingWeightPolicy",
                    invalidPolicy,
                ),
            )

            val ex = assertFailsWith<BinarySerializationException> {
                deserialize<PathOptions>(invalidPath)
            }
            log.debug { "ex.cause=${ex.cause}" }
            ex.cause.shouldNotBeNull().also {
                it.message shouldContain "default weight"
                it.message shouldContain "0.0"
            }
        }

        @Test
        fun `non-null option property의 invalid serialized payload도 거부된다`() {
            val invalidPayloads = listOf(
                serialize(objectWithField(NeighborOptions(), "direction", null)) to "direction",
                serialize(objectWithField(PathOptions(), "direction", null)) to "direction",
                serialize(objectWithField(PathOptions(), "missingWeightPolicy", null)) to "missingWeightPolicy",
                serialize(objectWithField(BfsDfsOptions(), "direction", null)) to "direction",
                serialize(objectWithField(DegreeOptions(), "direction", null)) to "direction",
            )

            invalidPayloads.forEach { (payload, expectedMessagePart) ->
                val ex = assertFailsWith<BinarySerializationException> {
                    deserialize<Any>(payload)
                }
                log.debug { "ex.cause=${ex.cause}" }
                ex.cause.shouldNotBeNull().message shouldContain expectedMessagePart
            }
        }

        private val serializer = JdkBinarySerializer(objectInputFilter = null)

        private fun serialize(value: Any): ByteArray =
            serializer.serialize(value)

        private inline fun <reified T: Any> deserialize(bytes: ByteArray): T =
            serializer.deserialize<T>(bytes).shouldNotBeNull()

        private fun serializedWithField(value: Any, fieldName: String, replacement: Any?): ByteArray {
            return serialize(objectWithField(value, fieldName, replacement))
        }
    }

    @Nested
    inner class ForySerializationTest {
        @Test
        fun `traversal option들은 모든 public property를 Apache Fory serialization round-trip에서 보존한다`() {
            val options = listOf(
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 0),
                PathOptions(
                    edgeLabel = "ROAD",
                    maxDepth = 2,
                    weightProperty = "cost",
                    missingWeightPolicy = MissingWeightPolicy.UseDefault(1.5),
                    direction = Direction.INCOMING,
                    maxVisited = 200,
                ),
                BfsDfsOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING, maxDepth = 0, maxVertices = 25),
                CycleOptions(vertexLabel = "Person", edgeLabel = "KNOWS", maxDepth = 3, maxCycles = 7),
            )

            options.forEach { option ->
                deserialize<GraphTraversalOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        @Test
        fun `algorithm option들은 모든 public property를 Apache Fory serialization round-trip에서 보존한다`() {
            val options = listOf(
                PageRankOptions(
                    vertexLabel = "Person",
                    edgeLabel = "KNOWS",
                    iterations = 30,
                    dampingFactor = 0.9,
                    tolerance = 1e-6,
                    topK = 10,
                ),
                DegreeOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING),
                ComponentOptions(vertexLabel = "Person", edgeLabel = "KNOWS", weakly = false, minSize = 2),
            )

            options.forEach { option ->
                deserialize<GraphAlgorithmOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        private fun serialize(value: Any): ByteArray =
            BinarySerializers.FastFory.serialize(value)

        private inline fun <reified T: Any> deserialize(bytes: ByteArray): T =
            BinarySerializers.FastFory.deserialize<T>(bytes).shouldNotBeNull()
    }

    @Nested
    inner class KryoSerializationTest {
        @Test
        fun `traversal option들은 모든 public property를 Kryo serialization round-trip에서 보존한다`() {
            val options = listOf(
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 0),
                PathOptions(
                    edgeLabel = "ROAD",
                    maxDepth = 2,
                    weightProperty = "cost",
                    missingWeightPolicy = MissingWeightPolicy.UseDefault(1.5),
                    direction = Direction.INCOMING,
                    maxVisited = 200,
                ),
                BfsDfsOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING, maxDepth = 0, maxVertices = 25),
                CycleOptions(vertexLabel = "Person", edgeLabel = "KNOWS", maxDepth = 3, maxCycles = 7),
            )

            options.forEach { option ->
                deserialize<GraphTraversalOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        @Test
        fun `algorithm option들은 모든 public property를 Kryo serialization round-trip에서 보존한다`() {
            val options = listOf(
                PageRankOptions(
                    vertexLabel = "Person",
                    edgeLabel = "KNOWS",
                    iterations = 30,
                    dampingFactor = 0.9,
                    tolerance = 1e-6,
                    topK = 10,
                ),
                DegreeOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING),
                ComponentOptions(vertexLabel = "Person", edgeLabel = "KNOWS", weakly = false, minSize = 2),
            )

            options.forEach { option ->
                deserialize<GraphAlgorithmOptions>(serialize(option)) shouldBeEqualTo option
            }
        }

        private fun serialize(value: Any): ByteArray =
            BinarySerializers.Kryo.serialize(value)

        private inline fun <reified T: Any> deserialize(bytes: ByteArray): T =
            BinarySerializers.Kryo.deserialize<T>(bytes).shouldNotBeNull()

    }
}

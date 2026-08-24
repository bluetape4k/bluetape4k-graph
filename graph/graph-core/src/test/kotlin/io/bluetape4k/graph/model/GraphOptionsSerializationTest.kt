package io.bluetape4k.graph.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.lang.reflect.Modifier

class GraphOptionsSerializationTest {

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
            ObjectStreamClass.lookup(option.javaClass).serialVersionUID shouldBeEqualTo 1L
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
            val ex = assertFailsWith<InvalidObjectException> {
                deserialize<Any>(payload)
            }
            expectedMessageParts.forEach { ex.message shouldContain it }
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
            val ex = assertFailsWith<InvalidObjectException> {
                deserialize<Any>(payload)
            }
            expectedMessageParts.forEach { ex.message shouldContain it }
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

        val ex = assertFailsWith<InvalidObjectException> {
            deserialize<PathOptions>(invalidPath)
        }

        ex.message shouldContain "default weight"
        ex.message shouldContain "0.0"
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
            val ex = assertFailsWith<InvalidObjectException> {
                deserialize<Any>(payload)
            }
            ex.message shouldContain expectedMessagePart
        }
    }

    private fun serialize(value: Any): ByteArray = ByteArrayOutputStream().use { output ->
        ObjectOutputStream(output).use { it.writeObject(value) }
        output.toByteArray()
    }

    private inline fun <reified T> deserialize(bytes: ByteArray): T =
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }

    private fun serializedWithField(value: Any, fieldName: String, replacement: Any?): ByteArray {
        return serialize(objectWithField(value, fieldName, replacement))
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

    private companion object {
        val unsafe: Unsafe by lazy {
            Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        }
    }
}

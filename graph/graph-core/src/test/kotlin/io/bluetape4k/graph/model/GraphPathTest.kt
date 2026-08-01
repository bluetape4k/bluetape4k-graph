package io.bluetape4k.graph.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContainSame
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class GraphPathTest {

    private fun vertex(id: String, label: String = "Person") =
        GraphVertex(GraphElementId(id), label)

    private fun edge(id: String, start: String, end: String, label: String = "KNOWS") =
        GraphEdge(
            GraphElementId(id),
            label,
            GraphElementId(start),
            GraphElementId(end)
        )

    @Test
    fun `EMPTY 경로는 비어있다`() {
        GraphPath.EMPTY.isEmpty.shouldBeTrue()
        GraphPath.EMPTY.vertices.shouldBeEmpty()
        GraphPath.EMPTY.edges.shouldBeEmpty()
        GraphPath.EMPTY.length shouldBeEqualTo 0
    }

    @Test
    fun `of vararg로 정점만 있는 경로를 만든다`() {
        val v1 = vertex("1")
        val v2 = vertex("2")
        val path = GraphPath.of(v1, v2)

        path.vertices shouldContainSame listOf(v1, v2)
        path.edges.shouldBeEmpty()
        path.length shouldBeEqualTo 0
        path.isEmpty.shouldBeFalse()
    }

    @Test
    fun `정점-간선-정점 교차 경로에서 vertices와 edges를 분리한다`() {
        val v1 = vertex("1")
        val v2 = vertex("2")
        val v3 = vertex("3")
        val e1 = edge("e1", "1", "2")
        val e2 = edge("e2", "2", "3")

        val path = GraphPath(
            steps = listOf(
                PathStep.VertexStep(v1),
                PathStep.EdgeStep(e1),
                PathStep.VertexStep(v2),
                PathStep.EdgeStep(e2),
                PathStep.VertexStep(v3),
            ),
        )

        path.vertices shouldHaveSize 3
        path.vertices shouldContainSame listOf(v1, v2, v3)
        path.edges shouldHaveSize 2
        path.edges shouldContainSame listOf(e1, e2)
        path.length shouldBeEqualTo 2
        path.isEmpty.shouldBeFalse()
    }

    @Test
    fun `length는 edges 수로 정의된다`() {
        val single = GraphPath.of(vertex("1"))
        single.length shouldBeEqualTo 0

        val twoHop = GraphPath(
            steps = listOf(
                PathStep.VertexStep(vertex("1")),
                PathStep.EdgeStep(edge("e1", "1", "2")),
                PathStep.VertexStep(vertex("2")),
            ),
        )
        twoHop.length shouldBeEqualTo 1
    }

    @Test
    fun `PathStep은 sealed로 Vertex와 Edge만 있다`() {
        val vStep: PathStep = PathStep.VertexStep(vertex("1"))
        val eStep: PathStep = PathStep.EdgeStep(edge("e1", "1", "2"))

        vStep shouldBeInstanceOf PathStep.VertexStep::class
        eStep shouldBeInstanceOf PathStep.EdgeStep::class
    }

    @Test
    fun `단일 정점 경로는 length가 0이고 비어있지 않다`() {
        val path = GraphPath.of(vertex("1"))

        path.length shouldBeEqualTo 0
        path.isEmpty.shouldBeFalse()
        path.vertices shouldHaveSize 1
        path.edges.shouldBeEmpty()
    }

    @Test
    fun `EMPTY는 싱글턴이다`() {
        GraphPath.EMPTY shouldBeEqualTo GraphPath(emptyList())
        GraphPath.EMPTY.isEmpty.shouldBeTrue()
    }

    @Test
    fun `채워진 경로는 Java serialization round-trip을 지원한다`() {
        val v1 = GraphVertex(
            GraphElementId("1"),
            "Person",
            linkedMapOf("profile" to linkedMapOf("skills" to arrayListOf("kotlin", "graph")), "active" to true),
        )
        val v2 = GraphVertex(
            GraphElementId("2"),
            "Person",
            linkedMapOf("profile" to linkedMapOf("skills" to arrayListOf("java")), "active" to null),
        )
        val e1 = GraphEdge(
            GraphElementId("e1"),
            "KNOWS",
            GraphElementId("1"),
            GraphElementId("2"),
            linkedMapOf("metadata" to linkedMapOf("since" to 2024L, "trusted" to true)),
        )
        val path = GraphPath(
            steps = listOf(
                PathStep.VertexStep(v1),
                PathStep.EdgeStep(e1),
                PathStep.VertexStep(v2),
            ),
            totalWeight = 2.5,
        )

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(path) }
            output.toByteArray()
        }

        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as GraphPath
        }

        restored shouldBeEqualTo path
    }

    @Test
    fun `지원하지 않는 property 값은 Java serialization 계약 밖이다`() {
        val path = GraphPath.of(
            GraphVertex(GraphElementId("1"), "Person", mapOf("unsupported" to Any())),
        )

        assertFailsWith<NotSerializableException> {
            ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(path) }
        }
    }

    @Test
    fun `of(vararg) - 정점 없이 호출하면 빈 경로가 된다`() {
        val path = GraphPath.of()
        path.isEmpty.shouldBeTrue()
        path shouldBeEqualTo GraphPath.EMPTY
    }

    @Test
    fun `edges만 있는 steps에서 vertices는 비어있다`() {
        val e = edge("e1", "1", "2")
        val path = GraphPath(listOf(PathStep.EdgeStep(e)))

        path.edges shouldHaveSize 1
        path.vertices.shouldBeEmpty()
        path.length shouldBeEqualTo 1
    }

    @Test
    fun `copy로 steps를 교체한다`() {
        val original = GraphPath.of(vertex("1"), vertex("2"))
        val modified = original.copy(steps = emptyList())

        modified.isEmpty.shouldBeTrue()
        original.isEmpty.shouldBeFalse()
    }

    // --- graphPathOf 유틸 함수 테스트 ---

    @Test
    fun `graphPathOf PathStep vararg로 혼합 경로를 만든다`() {
        val v1 = vertex("1")
        val v2 = vertex("2")
        val e1 = edge("e1", "1", "2")
        val path = graphPathOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1), PathStep.VertexStep(v2))
        path.vertices shouldHaveSize 2
        path.edges shouldHaveSize 1
        path.length shouldBeEqualTo 1
    }

    @Test
    fun `graphPathOf PathStep List로 경로를 만든다`() {
        val v1 = vertex("1")
        val steps = listOf(PathStep.VertexStep(v1))
        val path = graphPathOf(steps)
        path.vertices shouldHaveSize 1
        path.isEmpty.shouldBeFalse()
    }

    @Test
    fun `graphPathOf GraphVertex vararg로 정점만 있는 경로를 만든다`() {
        val v1 = vertex("1")
        val v2 = vertex("2")
        val path = graphPathOf(v1, v2)
        path.vertices shouldContainSame listOf(v1, v2)
        path.edges.shouldBeEmpty()
        path.length shouldBeEqualTo 0
    }

    @Test
    fun `graphPathOf GraphVertex List로 경로를 만든다`() {
        val vertices = listOf(vertex("1"), vertex("2"))
        val path = graphPathOf(vertices)
        path.vertices shouldContainSame vertices
        path.edges.shouldBeEmpty()
    }

    @Test
    fun `graphPathOf GraphEdge vararg로 간선만 있는 경로를 만든다`() {
        val e1 = edge("e1", "1", "2")
        val e2 = edge("e2", "2", "3")
        val path = graphPathOf(e1, e2)
        path.edges shouldContainSame listOf(e1, e2)
        path.vertices.shouldBeEmpty()
        path.length shouldBeEqualTo 2
    }

    @Test
    fun `graphPathOf GraphEdge List로 경로를 만든다`() {
        val edges = listOf(edge("e1", "1", "2"))
        val path = graphPathOf(edges)
        path.edges shouldContainSame edges
        path.vertices.shouldBeEmpty()
    }

    @Test
    fun `emptyGraphPath는 GraphPath EMPTY와 동일하다`() {
        val path = emptyGraphPath()
        path.isEmpty.shouldBeTrue()
        path shouldBeEqualTo GraphPath.EMPTY
    }

    @Test
    fun `graphPathOf vararg 단일 정점으로 경로를 만든다`() {
        val v = vertex("1")
        val path = graphPathOf(v)
        path.vertices shouldHaveSize 1
        path.edges.shouldBeEmpty()
    }

    @Test
    fun `graphPathOf vararg 단일 간선으로 경로를 만든다`() {
        val e = edge("e1", "1", "2")
        val path = graphPathOf(e)
        path.edges shouldHaveSize 1
        path.vertices.shouldBeEmpty()
    }
}

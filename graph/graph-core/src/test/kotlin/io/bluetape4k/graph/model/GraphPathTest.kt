package io.bluetape4k.graph.model

import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

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
}

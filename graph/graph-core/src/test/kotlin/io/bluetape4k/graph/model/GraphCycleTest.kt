package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBe
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GraphCycleTest {

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
    fun `GraphCycle은 경로를 래핑한다`() {
        val v1 = vertex("1")
        val v2 = vertex("2")
        val e1 = edge("e1", "1", "2")
        val e2 = edge("e2", "2", "1")
        val path = GraphPath(
            listOf(
                PathStep.VertexStep(v1),
                PathStep.EdgeStep(e1),
                PathStep.VertexStep(v2),
                PathStep.EdgeStep(e2),
                PathStep.VertexStep(v1),
            )
        )

        val cycle = GraphCycle(path)
        cycle.path shouldBe path
        cycle.length shouldBeEqualTo 2
    }

    @Test
    fun `length는 경로의 간선 수다`() {
        val v1 = vertex("1")
        val e1 = edge("e1", "1", "1")
        val path = GraphPath(listOf(PathStep.VertexStep(v1), PathStep.EdgeStep(e1), PathStep.VertexStep(v1)))

        val cycle = GraphCycle(path)
        cycle.length shouldBeEqualTo 1
    }

    @Test
    fun `빈 경로로 만든 GraphCycle의 length는 0이다`() {
        val cycle = GraphCycle(GraphPath.EMPTY)
        cycle.length shouldBeEqualTo 0
    }

    @Test
    fun `GraphPath toCycle 확장 함수가 GraphCycle을 반환한다`() {
        val v1 = vertex("a")
        val v2 = vertex("b")
        val e1 = edge("e1", "a", "b")
        val e2 = edge("e2", "b", "a")
        val path = GraphPath(
            listOf(
                PathStep.VertexStep(v1),
                PathStep.EdgeStep(e1),
                PathStep.VertexStep(v2),
                PathStep.EdgeStep(e2),
                PathStep.VertexStep(v1),
            )
        )

        val cycle = path.toCycle()

        cycle shouldBeInstanceOf GraphCycle::class
        cycle.path shouldBe path
        cycle.length shouldBeEqualTo 2
    }

    @Test
    fun `GraphPath EMPTY에서 toCycle을 호출하면 length 0인 순환을 반환한다`() {
        val cycle = GraphPath.EMPTY.toCycle()
        cycle.length shouldBeEqualTo 0
    }

    @Test
    fun `단일 간선 경로에서 toCycle이 동작한다`() {
        val e = edge("e1", "1", "2")
        val path = GraphPath(listOf(PathStep.EdgeStep(e)))
        val cycle = path.toCycle()
        cycle.length shouldBeEqualTo 1
    }

    @Test
    fun `GraphCycle은 data class이므로 동등 비교가 가능하다`() {
        val v1 = vertex("1")
        val path = GraphPath.of(v1)
        val c1 = GraphCycle(path)
        val c2 = GraphCycle(path)
        c1 shouldBeEqualTo c2
    }
}

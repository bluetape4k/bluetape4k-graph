package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GraphAlgorithmResultsTest {

    @Test
    fun `DegreeResult total은 inDegree와 outDegree의 합이다`() {
        val result = DegreeResult(GraphElementId("v-1"), inDegree = 2, outDegree = 3)

        result.vertexId shouldBeEqualTo GraphElementId("v-1")
        result.inDegree shouldBeEqualTo 2
        result.outDegree shouldBeEqualTo 3
        result.total shouldBeEqualTo 5
    }

    @Test
    fun `DegreeResult copy로 degree 값을 변경한다`() {
        val base = DegreeResult(GraphElementId("v-1"), inDegree = 1, outDegree = 4)
        val updated = base.copy(outDegree = 7)

        updated.vertexId shouldBeEqualTo GraphElementId("v-1")
        updated.inDegree shouldBeEqualTo 1
        updated.outDegree shouldBeEqualTo 7
        updated.total shouldBeEqualTo 8
    }

    @Test
    fun `PageRankScore는 vertex와 score를 보존한다`() {
        val vertex = GraphVertex(GraphElementId("account-1"), "Account", mapOf("risk" to "high"))
        val score = PageRankScore(vertex, score = 0.42)

        score.vertex shouldBeEqualTo vertex
        score.score shouldBeEqualTo 0.42
    }

    @Test
    fun `PageRankScore copy로 score만 변경한다`() {
        val vertex = GraphVertex(GraphElementId("account-1"), "Account")
        val score = PageRankScore(vertex, score = 0.42)
        val updated = score.copy(score = 0.51)

        updated.vertex shouldBeEqualTo vertex
        updated.score shouldBeEqualTo 0.51
    }

    @Test
    fun `GraphComponent size는 vertex 개수이다`() {
        val vertices = listOf(
            GraphVertex(GraphElementId("a"), "Account"),
            GraphVertex(GraphElementId("b"), "Account"),
        )
        val component = GraphComponent("component-1", vertices)

        component.componentId shouldBeEqualTo "component-1"
        component.vertices shouldBeEqualTo vertices
        component.size shouldBeEqualTo 2
    }

    @Test
    fun `GraphComponent copy로 vertices만 변경한다`() {
        val component = GraphComponent("component-1", listOf(GraphVertex(GraphElementId("a"), "Account")))
        val updated = component.copy(
            vertices = component.vertices + GraphVertex(GraphElementId("b"), "Account")
        )

        updated.componentId shouldBeEqualTo "component-1"
        updated.size shouldBeEqualTo 2
    }

    @Test
    fun `algorithm result model들은 Serializable이다`() {
        val degree: java.io.Serializable = DegreeResult(GraphElementId("v-1"), inDegree = 1, outDegree = 2)
        val score: java.io.Serializable = PageRankScore(GraphVertex(GraphElementId("v-1"), "Person"), score = 0.7)
        val component: java.io.Serializable = GraphComponent("component-1", emptyList())

        degree shouldBeInstanceOf java.io.Serializable::class
        score shouldBeInstanceOf java.io.Serializable::class
        component shouldBeInstanceOf java.io.Serializable::class
    }
}

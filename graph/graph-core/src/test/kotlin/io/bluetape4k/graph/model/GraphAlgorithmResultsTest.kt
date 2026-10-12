package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphAlgorithmResultsTest: AbstractGraphTest() {

    companion object: KLogging()

    @Test
    fun `DegreeResult total은 inDegree와 outDegree의 합이다`() {
        val result = DegreeResult(id("v-1"), inDegree = 2, outDegree = 3)

        log.debug { "result=$result" }
        result.vertexId shouldBeEqualTo id("v-1")
        result.inDegree shouldBeEqualTo 2
        result.outDegree shouldBeEqualTo 3
        result.total shouldBeEqualTo 5
    }

    @Test
    fun `DegreeResult copy로 degree 값을 변경한다`() {
        val base = DegreeResult(id("v-1"), inDegree = 1, outDegree = 4)
        val updated = base.copy(outDegree = 7)

        log.debug { "updated=$updated" }
        updated.vertexId shouldBeEqualTo id("v-1")
        updated.inDegree shouldBeEqualTo 1
        updated.outDegree shouldBeEqualTo 7
        updated.total shouldBeEqualTo 8
    }

    @Test
    fun `PageRankScore는 vertex와 score를 보존한다`() {
        val vertex = vertex("account-1", "Account", mapOf("risk" to "high"))
        val score = PageRankScore(vertex, score = 0.42)

        log.debug { "score=$score" }
        score.vertex shouldBeEqualTo vertex
        score.score shouldBeEqualTo 0.42
    }

    @Test
    fun `PageRankScore copy로 score만 변경한다`() {
        val vertex = vertex("account-1", "Account")
        val score = PageRankScore(vertex, score = 0.42)
        val updated = score.copy(score = 0.51)

        log.debug { "updated=$updated" }
        updated.vertex shouldBeEqualTo vertex
        updated.score shouldBeEqualTo 0.51
    }

    @Test
    fun `GraphComponent size는 vertex 개수이다`() {
        val vertices = listOf(
            vertex("a", "Account"),
            vertex("b", "Account"),
        )
        val component = GraphComponent("component-1", vertices)

        log.debug { "component=$component" }
        component.componentId shouldBeEqualTo "component-1"
        component.vertices shouldBeEqualTo vertices
        component.size shouldBeEqualTo 2
    }

    @Test
    fun `GraphComponent copy로 vertices만 변경한다`() {
        val component = GraphComponent("component-1", listOf(vertex("a", "Account")))
        val updated = component.copy(
            vertices = component.vertices + vertex("b", "Account")
        )

        log.debug { "updated=$updated" }
        updated.componentId shouldBeEqualTo "component-1"
        updated.size shouldBeEqualTo 2
    }

    @Test
    fun `algorithm result model들은 Serializable이다`() {
        val degree = DegreeResult(id("v-1"), inDegree = 1, outDegree = 2)
        val score = PageRankScore(vertex("v-1", "Person"), score = 0.7)
        val component = GraphComponent("component-1", emptyList())

        degree shouldBeInstanceOf java.io.Serializable::class
        score shouldBeInstanceOf java.io.Serializable::class
        component shouldBeInstanceOf java.io.Serializable::class
    }
}

package io.bluetape4k.graph.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Test

class GraphEdgeTest {

    private val edgeId = GraphElementId("e-1")
    private val startId = GraphElementId("v-1")
    private val endId = GraphElementId("v-2")

    @Test
    fun `시작 정점과 도착 정점을 가지는 간선을 만든다`() {
        val edge = GraphEdge(
            id = edgeId,
            label = "KNOWS",
            startId = startId,
            endId = endId,
            properties = mapOf("since" to 2020),
        )

        edge.id shouldBeEqualTo edgeId
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo startId
        edge.endId shouldBeEqualTo endId
        edge.properties["since"] shouldBeEqualTo 2020
    }

    @Test
    fun `properties 기본값은 빈 맵이다`() {
        val edge = GraphEdge(edgeId, "KNOWS", startId, endId)
        edge.properties shouldBeEqualTo emptyMap()
    }

    @Test
    fun `자기 자신을 가리키는 간선도 허용된다`() {
        val self = GraphEdge(edgeId, "LOOP", startId, startId)
        self.startId shouldBeEqualTo self.endId
    }

    @Test
    fun `동일한 필드로 만든 간선은 동등하다`() {
        val a = GraphEdge(edgeId, "KNOWS", startId, endId, mapOf("k" to "v"))
        val b = GraphEdge(edgeId, "KNOWS", startId, endId, mapOf("k" to "v"))
        b shouldBeEqualTo a
    }

    @Test
    fun `방향이 반대이면 다른 간선이다`() {
        val forward = GraphEdge(edgeId, "KNOWS", startId, endId)
        val reversed = GraphEdge(edgeId, "KNOWS", endId, startId)

        reversed shouldNotBeEqualTo forward
    }
}

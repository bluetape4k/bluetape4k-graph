package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEqualTo
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphEdgeTest: AbstractGraphTest() {

    companion object: KLogging()

    private val edgeId = "e-1".toGraphElementId()
    private val startId = "v-1".toGraphElementId()
    private val endId = "v-2".toGraphElementId()

    @Test
    fun `시작 정점과 도착 정점을 가지는 간선을 만든다`() {
        val edge = edgeOf(
            id = edgeId,
            label = "KNOWS",
            startId = startId,
            endId = endId,
            "since" to 2020,
        )

        log.debug { "edge=$edge" }
        edge.id shouldBeEqualTo edgeId
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo startId
        edge.endId shouldBeEqualTo endId
        edge.properties["since"] shouldBeEqualTo 2020
    }

    @Test
    fun `properties 기본값은 빈 맵이다`() {
        val edge = edgeOf(edgeId, "KNOWS", startId, endId)

        log.debug { "edge=$edge" }
        edge.properties shouldBeEqualTo emptyMap()
    }

    @Test
    fun `자기 자신을 가리키는 간선도 허용된다`() {
        val self = edgeOf(edgeId, "LOOP", startId, startId)

        log.debug { "self=$self" }
        self.startId shouldBeEqualTo self.endId
    }

    @Test
    fun `동일한 필드로 만든 간선은 동등하다`() {
        val a = edgeOf(edgeId, "KNOWS", startId, endId, "k" to "v")
        val b = edgeOf(edgeId, "KNOWS", startId, endId, "k" to "v")
        b shouldBeEqualTo a
    }

    @Test
    fun `방향이 반대이면 다른 간선이다`() {
        val forward = edgeOf(edgeId, "KNOWS", startId, endId)
        val reversed = edgeOf(edgeId, "KNOWS", endId, startId)

        reversed shouldNotBeEqualTo forward
    }

    @Test
    fun `null 값을 포함한 properties도 허용된다`() {
        val edge = edgeOf(edgeId, "KNOWS", startId, endId, "weight" to null)

        log.debug { "edge=$edge" }
        edge.properties.keys shouldContain "weight"
        edge.properties["weight"].shouldBeNull()
    }

    @Test
    fun `copy로 properties만 변경한다`() {
        val original = edgeOf(edgeId, "KNOWS", startId, endId, "since" to 2020)
        val updated = original.copy(properties = mapOf("since" to 2024))

        log.debug { "updated=$updated" }
        updated.id shouldBeEqualTo original.id
        updated.label shouldBeEqualTo original.label
        updated.startId shouldBeEqualTo original.startId
        updated.endId shouldBeEqualTo original.endId
        updated.properties["since"] shouldBeEqualTo 2024
    }

    @Test
    fun `다른 레이블을 가지면 동등하지 않다`() {
        val a = edgeOf(edgeId, "KNOWS", startId, endId)
        val b = edgeOf(edgeId, "LIKES", startId, endId)
        a shouldNotBeEqualTo b
    }
}

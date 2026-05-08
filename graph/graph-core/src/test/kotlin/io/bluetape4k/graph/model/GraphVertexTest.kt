package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEqualTo
import org.junit.jupiter.api.Test

class GraphVertexTest {

    private val id = GraphElementId("v-1")

    @Test
    fun `label과 properties로 정점을 만든다`() {
        val v = GraphVertex(
            id = id,
            label = "Person",
            properties = mapOf("name" to "Alice", "age" to 30),
        )

        v.id shouldBeEqualTo id
        v.label shouldBeEqualTo "Person"
        v.properties["name"] shouldBeEqualTo "Alice"
        v.properties["age"] shouldBeEqualTo 30
    }

    @Test
    fun `properties 기본값은 빈 맵이다`() {
        val v = GraphVertex(id = id, label = "Person")
        v.properties.shouldBeEmpty()
    }

    @Test
    fun `data class 동등성은 id-label-properties에 의해 결정된다`() {
        val a = GraphVertex(id, "Person", mapOf("k" to 1))
        val b = GraphVertex(id, "Person", mapOf("k" to 1))
        val c = GraphVertex(id, "Person", mapOf("k" to 2))

        b shouldBeEqualTo a
        c shouldNotBeEqualTo a
    }

    @Test
    fun `copy로 properties만 변경한다`() {
        val v = GraphVertex(id, "Person", mapOf("name" to "Alice"))
        val updated = v.copy(properties = mapOf("name" to "Bob"))

        updated.id shouldBeEqualTo v.id
        updated.label shouldBeEqualTo v.label
        updated.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    fun `null 값을 포함한 properties도 허용된다`() {
        val v = GraphVertex(id, "Person", mapOf("nickname" to null))
        v.properties.keys shouldContain "nickname"
        v.properties["nickname"].shouldBeNull()
    }

    @Test
    fun `중첩 맵을 포함한 properties도 허용된다`() {
        val nested = mapOf("address" to mapOf("city" to "Seoul", "zip" to "04524"))
        val v = GraphVertex(id, "Person", nested)
        @Suppress("UNCHECKED_CAST")
        val address = v.properties["address"] as Map<String, Any?>
        address["city"] shouldBeEqualTo "Seoul"
    }

    @Test
    fun `copy로 label만 변경한다`() {
        val original = GraphVertex(id, "Person", mapOf("name" to "Alice"))
        val renamed = original.copy(label = "Employee")

        renamed.id shouldBeEqualTo original.id
        renamed.label shouldBeEqualTo "Employee"
        renamed.properties shouldBeEqualTo original.properties
    }

    @Test
    fun `서로 다른 id를 가진 정점은 동등하지 않다`() {
        val v1 = GraphVertex(GraphElementId("a"), "Person")
        val v2 = GraphVertex(GraphElementId("b"), "Person")
        v1 shouldNotBeEqualTo v2
    }

    @Test
    fun `서로 다른 label을 가진 정점은 동등하지 않다`() {
        val v1 = GraphVertex(id, "Person")
        val v2 = GraphVertex(id, "Company")
        v1 shouldNotBeEqualTo v2
    }

    // --- graphVertexOf 유틸 함수 테스트 ---

    @Test
    fun `graphVertexOf - GraphElementId로 정점을 생성한다`() {
        val v = graphVertexOf(id, "Person", mapOf("name" to "Alice"))
        v.id shouldBeEqualTo id
        v.label shouldBeEqualTo "Person"
        v.properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    fun `graphVertexOf - GraphElementId + label만으로 빈 properties 정점을 생성한다`() {
        val v = graphVertexOf(id, "Person")
        v.id shouldBeEqualTo id
        v.label shouldBeEqualTo "Person"
        v.properties.shouldBeEmpty()
    }

    @Test
    fun `graphVertexOf - Any id String으로 정점을 생성한다`() {
        val v = graphVertexOf("v-2", "Company")
        v.id shouldBeEqualTo GraphElementId("v-2")
        v.label shouldBeEqualTo "Company"
    }

    @Test
    fun `graphVertexOf - Any id Long으로 정점을 생성한다`() {
        val v = graphVertexOf(42L, "Item")
        v.id.value shouldBeEqualTo "42"
        v.label shouldBeEqualTo "Item"
    }

    @Test
    fun `graphVertexOf - Any id GraphElementId 전달 시 이중 변환 없다`() {
        val existing = GraphElementId("original")
        val v = graphVertexOf(existing, "Node")
        // 이중 toString 변환 시 "GraphElementId(value=original)" 이 됨을 방지
        v.id.value shouldBeEqualTo "original"
    }

    @Test
    fun `graphVertexOf - Any id에 properties도 전달할 수 있다`() {
        val v = graphVertexOf("v-3", "Person", mapOf("age" to 30))
        v.id.value shouldBeEqualTo "v-3"
        v.properties["age"] shouldBeEqualTo 30
    }
}

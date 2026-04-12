package io.bluetape4k.graph.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Test

class GraphElementIdTest {

    @Test
    fun `생성자로 String 값을 직접 감싼다`() {
        val id = GraphElementId("abc-123")
        id.value shouldBeEqualTo "abc-123"
    }

    @Test
    fun `of(String) 팩토리는 동일한 값을 생성한다`() {
        val id = GraphElementId.of("node-1")
        id shouldBeEqualTo GraphElementId("node-1")
    }

    @Test
    fun `of(Long) 팩토리는 숫자 ID를 문자열로 변환한다`() {
        val id = GraphElementId.of(42L)
        id.value shouldBeEqualTo "42"
        id shouldBeEqualTo GraphElementId("42")
    }

    @Test
    fun `같은 값의 GraphElementId는 동등하다`() {
        GraphElementId("x") shouldBeEqualTo GraphElementId("x")
        GraphElementId("x") shouldNotBeEqualTo GraphElementId("y")
    }

    @Test
    fun `빈 문자열도 허용된다`() {
        GraphElementId("").value shouldBeEqualTo ""
    }
}

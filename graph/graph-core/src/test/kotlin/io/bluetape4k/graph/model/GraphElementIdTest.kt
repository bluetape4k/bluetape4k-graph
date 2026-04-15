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

    @Test
    fun `of(Long) - 0은 문자열 0으로 변환된다`() {
        GraphElementId.of(0L).value shouldBeEqualTo "0"
    }

    @Test
    fun `of(Long) - 음수도 문자열로 변환된다`() {
        GraphElementId.of(-1L).value shouldBeEqualTo "-1"
    }

    @Test
    fun `of(Long) - Long MAX_VALUE도 문자열로 변환된다`() {
        val id = GraphElementId.of(Long.MAX_VALUE)
        id.value shouldBeEqualTo Long.MAX_VALUE.toString()
    }

    @Test
    fun `특수 문자를 포함한 ID도 허용된다`() {
        val id = GraphElementId("node:type/path#fragment")
        id.value shouldBeEqualTo "node:type/path#fragment"
    }

    @Test
    fun `유니코드 문자열도 허용된다`() {
        val id = GraphElementId("정점-아이디")
        id.value shouldBeEqualTo "정점-아이디"
    }

    @Test
    fun `value class - toString이 값을 포함한다`() {
        val id = GraphElementId("test-123")
        id.toString() shouldBeEqualTo "GraphElementId(value=test-123)"
    }
}

package io.bluetape4k.graph.utils

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldEndWith
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class GraphPropertiesTest {

    // ----- toCypherValue -----

    @Test
    fun `null은 Cypher null 리터럴로 변환된다`() {
        GraphProperties.toCypherValue(null) shouldBeEqualTo "null"
    }

    @Test
    fun `문자열은 작은따옴표로 감싼다`() {
        GraphProperties.toCypherValue("Alice") shouldBeEqualTo "'Alice'"
    }

    @Test
    fun `문자열 내부 작은따옴표는 escape 된다`() {
        // "it's" -> 'it\'s'
        val encoded = GraphProperties.toCypherValue("it's")
        encoded shouldStartWith "'"
        encoded shouldEndWith "'"
        encoded shouldContain "\\'"
    }

    @Test
    fun `백슬래시는 이스케이프되며 작은따옴표 이스케이프보다 먼저 처리된다`() {
        // 입력: a\b  ->  'a\\b'
        GraphProperties.toCypherValue("a\\b") shouldBeEqualTo "'a\\\\b'"
        // 입력: \'  ->  '\\\''
        GraphProperties.toCypherValue("\\'") shouldBeEqualTo "'\\\\\\''"
    }

    @Test
    fun `개행과 탭 문자는 escape 된다`() {
        GraphProperties.toCypherValue("a\nb") shouldBeEqualTo "'a\\nb'"
        GraphProperties.toCypherValue("a\rb") shouldBeEqualTo "'a\\rb'"
        GraphProperties.toCypherValue("a\tb") shouldBeEqualTo "'a\\tb'"
    }

    @Test
    fun `빈 문자열도 작은따옴표로 감싼다`() {
        GraphProperties.toCypherValue("") shouldBeEqualTo "''"
    }

    @Test
    fun `Int, Long, Double은 숫자 리터럴로 변환된다`() {
        GraphProperties.toCypherValue(42) shouldBeEqualTo "42"
        GraphProperties.toCypherValue(42L) shouldBeEqualTo "42"
        GraphProperties.toCypherValue(3.14) shouldBeEqualTo "3.14"
    }

    @Test
    fun `Boolean은 true-false 리터럴로 변환된다`() {
        GraphProperties.toCypherValue(true) shouldBeEqualTo "true"
        GraphProperties.toCypherValue(false) shouldBeEqualTo "false"
    }

    @Test
    fun `List는 대괄호로 감싸고 요소를 재귀 변환한다`() {
        val list = listOf("a", 1, true, null)
        GraphProperties.toCypherValue(list) shouldBeEqualTo "['a', 1, true, null]"
    }

    @Test
    fun `빈 List는 대괄호 쌍으로 변환된다`() {
        GraphProperties.toCypherValue(emptyList<Any>()) shouldBeEqualTo "[]"
    }

    @Test
    fun `중첩 List도 재귀 변환된다`() {
        val nested = listOf(listOf(1, 2), listOf(3, 4))
        GraphProperties.toCypherValue(nested) shouldBeEqualTo "[[1, 2], [3, 4]]"
    }

    @Test
    fun `Map은 중괄호로 감싸고 요소를 재귀 변환한다`() {
        val map = linkedMapOf<String, Any?>("a" to 1, "b" to "x")
        GraphProperties.toCypherValue(map) shouldBeEqualTo "{a: 1, b: 'x'}"
    }

    @Test
    fun `빈 Map은 빈 중괄호 쌍으로 변환된다`() {
        GraphProperties.toCypherValue(emptyMap<String, Any?>()) shouldBeEqualTo "{}"
    }

    @Test
    fun `LocalDate는 ISO-8601 문자열 리터럴로 변환된다`() {
        val d = LocalDate.of(2026, 4, 12)
        GraphProperties.toCypherValue(d) shouldBeEqualTo "'2026-04-12'"
    }

    @Test
    fun `LocalDateTime은 ISO-8601 문자열 리터럴로 변환된다`() {
        val dt = LocalDateTime.of(2026, 4, 12, 9, 30, 0)
        GraphProperties.toCypherValue(dt) shouldBeEqualTo "'2026-04-12T09:30'"
    }

    @Test
    fun `알 수 없는 타입은 toString 후 작은따옴표로 감싼다`() {
        val value = object {
            override fun toString() = "custom"
        }
        GraphProperties.toCypherValue(value) shouldBeEqualTo "'custom'"
    }

    // ----- toCypherProps -----

    @Test
    fun `빈 맵은 빈 문자열을 반환한다`() {
        GraphProperties.toCypherProps(emptyMap()) shouldBeEqualTo ""
    }

    @Test
    fun `단일 키-값 맵은 중괄호로 감싼다`() {
        val props = mapOf("name" to "Alice")
        GraphProperties.toCypherProps(props) shouldBeEqualTo "{name: 'Alice'}"
    }

    @Test
    fun `여러 키를 콤마로 연결한다`() {
        val props = linkedMapOf<String, Any?>(
            "name" to "Alice",
            "age" to 30,
            "active" to true,
        )
        GraphProperties.toCypherProps(props) shouldBeEqualTo
                "{name: 'Alice', age: 30, active: true}"
    }

    @Test
    fun `null 값을 가진 프로퍼티도 포함된다`() {
        val props = mapOf("nickname" to null)
        GraphProperties.toCypherProps(props) shouldBeEqualTo "{nickname: null}"
    }

    @Test
    fun `List 값을 가진 프로퍼티도 변환된다`() {
        val props = mapOf("tags" to listOf("kotlin", "graph"))
        GraphProperties.toCypherProps(props) shouldBeEqualTo
                "{tags: ['kotlin', 'graph']}"
    }
}
